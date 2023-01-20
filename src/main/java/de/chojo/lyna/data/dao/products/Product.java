package de.chojo.lyna.data.dao.products;

import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Role;

import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Product {
    private final Products products;
    int id;
    String name;
    String url;
    long role;

    public Product(Products products, int id, String name, String url, long role) {
        this.products = products;
        this.id = id;
        this.name = name;
        this.url = url;
        this.role = role;
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

    public long role() {
        return role;
    }

    public boolean delete() {
        return builder().query("DELETE FROM product WHERE id = ? AND guild_id = ?")
                        .parameter(stmt -> stmt.setInt(id).setLong(products.guildId()))
                        .delete()
                        .sendSync()
                        .changed();
    }

    public long guildId() {
        return products.guildId();
    }

    public Products products() {
        return products;
    }

    public Optional<Role> role(Guild guild) {
        return Optional.ofNullable(guild.getRoleById(role));
    }
}
