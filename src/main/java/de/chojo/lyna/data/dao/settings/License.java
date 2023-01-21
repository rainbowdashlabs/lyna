package de.chojo.lyna.data.dao.settings;

import net.dv8tion.jda.api.entities.Guild;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class License {
    private final Settings settings;
    int shares = 0;

    public License(Settings settings) {
        this.settings = settings;
    }

    public License(Settings settings, int shares) {
        this.settings = settings;
        this.shares = shares;
    }

    public int shares() {
        return shares;
    }

    public void shares(int shares) {
        if (builder()
                .query("""
                       INSERT INTO license_settings(guild_id, shares) VALUES(?,?)
                       ON CONFLICT(guild_id)
                            DO UPDATE
                                SET shares = excluded.shares
                       """)
                .parameter(stmt -> stmt.setLong(guildId()).setInt(shares))
                .insert()
                .sendSync()
                .changed()) {
            this.shares = shares;
        }
    }

    public long guildId() {
        return settings.guildId();
    }

    public Guild guild() {
        return settings.guild();
    }
}
