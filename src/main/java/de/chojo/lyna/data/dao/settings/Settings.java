package de.chojo.lyna.data.dao.settings;

import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.entities.Guild;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Settings {
    private final LicenseGuild licenseGuild;

    private License license = null;

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
}
