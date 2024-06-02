package de.chojo.lyna.data.dao.settings;

import net.dv8tion.jda.api.entities.Guild;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

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
        if (query("""
                INSERT INTO license_settings(guild_id, shares) VALUES(?,?)
                ON CONFLICT(guild_id)
                     DO UPDATE
                         SET shares = excluded.shares
                """)
                .single(call().bind(guildId()).bind(shares))
                .insert()
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
