package de.chojo.lyna.data.dao.downloadtype;

import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class DownloadTypes {
    LicenseGuild guild;

    public DownloadTypes(LicenseGuild guild) {
        this.guild = guild;
    }

    public List<DownloadType> all() {
        return builder(DownloadType.class)
                .query("""
                        SELECT
                        	id,
                        	name,
                        	description,
                        	release_type
                        FROM
                        	download_type
                        WHERE guild_id = ?""")
                .parameter(stmt -> stmt.setLong(guild.guildId()))
                .readRow(r -> DownloadType.build(guild, r))
                .allSync();
    }

    public Optional<DownloadType> byId(int id) {
        return builder(DownloadType.class)
                .query("""
                        SELECT
                        	id,
                        	name,
                        	description,
                        	release_type
                        FROM
                        	download_type
                        WHERE guild_id = ?
                            AND id = ?""")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setInt(id))
                .readRow(r -> DownloadType.build(guild, r))
                .firstSync();
    }

    public List<Command.Choice> complete(String value) {
        return builder(DownloadType.class)
                .query("""
                        SELECT
                        	id,
                        	name,
                        	description,
                        	release_type
                        FROM
                        	download_type
                        WHERE guild_id = ?
                          AND name ILIKE ?""")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setString("%s%%".formatted(value)))
                .readRow(r -> DownloadType.build(guild, r))
                .allSync()
                .stream()
                .map(t -> new Command.Choice(t.name(), t.id()))
                .limit(25)
                .toList();
    }

    public Optional<DownloadType> create(String name, String description, ReleaseType releaseType) {
        return builder(DownloadType.class)
                .query("""
                        INSERT
                        INTO
                        	download_type(guild_id, name, description, release_type)
                        VALUES
                        	(?, ?, ?, ?::release_type)
                        ON CONFLICT DO NOTHING
                        RETURNING id, name, description, release_type""")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setString(name).setString(description).setEnum(releaseType))
                .readRow(row -> DownloadType.build(guild, row))
                .firstSync();
    }
}
