package de.chojo.lyna.data.dao.platforms;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Platform {
    Platforms platforms;
    int id;
    String name;
    String url;

    public Platform(Platforms platforms, int id, String name, String url) {
        this.platforms = platforms;
        this.id = id;
        this.name = name;
        this.url = url;
    }

    public Platforms platforms() {
        return platforms;
    }

    public int id() {
        return id;
    }

    public String name() {
        return name;
    }

    public String url() {
        return url;
    }

    public boolean delete() {
        return builder()
                .query("DELETE FROM platform WHERE id = ? and guild_id = ?")
                .parameter(stmt -> stmt.setInt(id).setLong(platforms.guildId()))
                .delete()
                .sendSync()
                .changed();
    }
}
