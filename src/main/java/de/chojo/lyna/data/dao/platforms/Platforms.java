package de.chojo.lyna.data.dao.platforms;

import de.chojo.lyna.data.dao.LicenseGuild;

import java.util.List;
import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Platforms {
    private final LicenseGuild guild;

    public Platforms(LicenseGuild guild) {

        this.guild = guild;
    }

    public Optional<Platform> create(String name, String url) {
        return builder(Platform.class)
                .query("INSERT INTO platform(guild_id, name, url) VALUES(?,?,?) RETURNING id")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setString(name).setString(url))
                .readRow(row -> new Platform(this, row.getInt("id"), name, url))
                .firstSync();
    }

    public List<Platform> all() {
        return builder(Platform.class)
                .query("SELECT id, guild_id, name, url FROM platform WHERE guild_id = ?")
                .parameter(stmt -> stmt.setLong(guild.guildId()))
                .readRow(row -> new Platform(this, row.getInt("id"), row.getString("name"), row.getString("url")))
                .allSync();
    }

    public Optional<Platform> byId(int id) {
        return builder(Platform.class)
                .query("SELECT id, guild_id, name, url FROM platform WHERE guild_id = ?")
                .parameter(stmt -> stmt.setLong(guild.guildId()))
                .readRow(row -> new Platform(this, row.getInt("id"), row.getString("name"), row.getString("url")))
                .firstSync();
    }


    public long guildId() {
        return guild.guildId();
    }
}
