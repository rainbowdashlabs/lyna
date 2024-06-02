package de.chojo.lyna.data.dao.downloadtype;

import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.interactions.commands.Command;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class DownloadTypes {
    LicenseGuild guild;

    public DownloadTypes(LicenseGuild guild) {
        this.guild = guild;
    }

    public List<DownloadType> all() {
        return query("""
                SELECT
                	id,
                	name,
                	description,
                	release_type
                FROM
                	download_type
                WHERE guild_id = ?""")
                .single(call().bind(guild.guildId()))
                .map(r -> DownloadType.build(guild, r))
                .all();
    }

    public Optional<DownloadType> byId(int id) {
        return query("""
                SELECT
                	id,
                	name,
                	description,
                	release_type
                FROM
                	download_type
                WHERE guild_id = ?
                    AND id = ?""")
                .single(call().bind(guild.guildId()).bind(id))
                .map(r -> DownloadType.build(guild, r))
                .first();
    }

    public List<Command.Choice> complete(String value) {
        return query("""
                SELECT
                	id,
                	name,
                	description,
                	release_type
                FROM
                	download_type
                WHERE guild_id = ?
                  AND name ILIKE ?""")
                .single(call().bind(guild.guildId()).bind("%s%%".formatted(value)))
                .map(r -> DownloadType.build(guild, r))
                .all()
                .stream()
                .map(t -> new Command.Choice(t.name(), t.id()))
                .limit(25)
                .toList();
    }

    public Optional<DownloadType> create(String name, String description, ReleaseType releaseType) {
        return query("""
                INSERT
                INTO
                	download_type(guild_id, name, description, release_type)
                VALUES
                	(?, ?, ?, ?::release_type)
                ON CONFLICT DO NOTHING
                RETURNING id, name, description, release_type""")
                .single(call().bind(guild.guildId()).bind(name).bind(description).bind(releaseType))
                .map(row -> DownloadType.build(guild, row))
                .first();
    }
}
