package de.chojo.lyna.data.dao.downloadtype;

import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.sadu.exceptions.ThrowingConsumer;
import de.chojo.sadu.wrapper.util.ParamBuilder;
import de.chojo.sadu.wrapper.util.Row;

import java.sql.SQLException;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

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
        if (set("name", stmt -> stmt.setString(name))) {
            this.name = name;
        }
    }

    public void description(String description) {
        if (set("description", stmt -> stmt.setString(description))) {
            this.description = description;
        }
    }

    public void releaseType(ReleaseType releaseType) {
        if (set("release_type", stmt -> stmt.setEnum(releaseType))) {
            this.releaseType = releaseType;
        }
    }

    private boolean set(String column, ThrowingConsumer<ParamBuilder, SQLException> consumer) {
        return builder().query("UPDATE download_type SET %s = ? WHERE id = ?", column)
                .parameter(stmt -> {
                    consumer.accept(stmt);
                    stmt.setInt(id);
                }).update()
                .sendSync()
                .changed();
    }

    public boolean delete() {
        return builder()
                .query("DELETE FROM download_type WHERE guild_id = ? AND id =  ?")
                .parameter(stmt -> stmt.setLong(guild.guildId()).setInt(id))
                .delete()
                .sendSync()
                .changed();
    }
}
