package de.chojo.lyna.data.dao.downloadtype;

import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.queries.api.call.Call;

import java.sql.SQLException;
import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class DownloadType {
    LicenseGuild guild;
    int id;
    String name;
    String description;
    ReleaseType releaseType;

    public DownloadType(LicenseGuild guild, int id, String name, String description, ReleaseType releaseType) {
        this.guild = guild;
        this.id = id;
        this.name = name;
        this.description = description;
        this.releaseType = releaseType;
    }

    public static DownloadType build(LicenseGuild guild, Row row) throws SQLException {
        return new DownloadType(guild,
                row.getInt("id"),
                row.getString("name"),
                row.getString("description"),
                row.getEnum("release_type", ReleaseType.class));
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String description() {
        return description;
    }

    public ReleaseType releaseType() {
        return releaseType;
    }

    public void name(String name) {
        if (set("name", stmt -> stmt.bind(name))) {
            this.name = name;
        }
    }

    public void description(String description) {
        if (set("description", stmt -> stmt.bind(description))) {
            this.description = description;
        }
    }

    public void releaseType(ReleaseType releaseType) {
        if (set("release_type", stmt -> stmt.bind(releaseType))) {
            this.releaseType = releaseType;
        }
    }

    private boolean set(String column, Function<Call, Call> consumer) {
        return query("UPDATE download_type SET %s = ? WHERE id = ?", column)
                .single(consumer.apply(call()).bind(id)).update()
                .changed();
    }

    public boolean delete() {
        return query("DELETE FROM download_type WHERE guild_id = ? AND id =  ?")
                .single(call().bind(guild.guildId()).bind(id))
                .delete()
                .changed();
    }
}
