package de.chojo.lyna.data.dao.settings;

import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.entities.Guild;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Settings {
    private final LicenseGuild licenseGuild;

    private License license = null;
    private Trial trial;

    public Settings(LicenseGuild licenseGuild) {
        this.licenseGuild = licenseGuild;
    }

    public LicenseGuild licenseGuild() {
        return licenseGuild;
    }

    public long guildId() {
        return licenseGuild.guildId();
    }

    public Guild guild() {
        return licenseGuild.guild();
    }

    public License license() {
        if (license == null) {
            license = builder(License.class)
                    .query("SELECT * FROM license_settings WHERE guild_id = ?")
                    .parameter(stmt -> stmt.setLong(guildId()))
                    .readRow(row -> new License(this, row.getInt("shares")))
                    .firstSync()
                    .orElseGet(() -> new License(this));
        }
        return license;
    }
    public Trial trial() {
        if (trial == null) {
            trial = builder(Trial.class)
                    .query("SELECT * FROM trial_settings WHERE guild_id = ?")
                    .parameter(stmt -> stmt.setLong(guildId()))
                    .readRow(row -> new Trial(this, row.getInt("server_time"), row.getInt("account_time")))
                    .firstSync()
                    .orElseGet(() -> new Trial(this));
        }
        return trial;
    }
}
