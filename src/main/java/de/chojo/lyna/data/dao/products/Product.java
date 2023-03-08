package de.chojo.lyna.data.dao.products;

import de.chojo.lyna.data.dao.products.downloads.Downloads;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.Optional;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class Product {
    private final Products products;
    private final NexusRest nexus;
    int id;
    String name;
    String url;
    long role;
    Downloads downloads;

    public Product(Products products, int id, String name, String url, long role) {
        this.products = products;
        this.nexus = products.nexus();
        this.id = id;
        this.name = name;
        this.url = url;
        this.role = role;
        downloads = new Downloads(this);
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

    public boolean canAccess(Member member) {
        return products.licenseGuild().user(member).canAccess(this);
    }

    public Optional<Role> role(Guild guild) {
        return Optional.ofNullable(guild.getRoleById(role));
    }

    public void assign(Member member) {
        Role roleById = member.getGuild().getRoleById(role);
        if (roleById != null && !member.getRoles().contains(roleById)) {
            member.getGuild().addRoleToMember(member, roleById).queue();
        }
    }

    public Downloads downloads() {
        return downloads;
    }

    public void revoke(Member member) {
        Role roleById = member.getGuild().getRoleById(role);
        if (roleById != null && member.getRoles().contains(roleById)) {
            member.getGuild().removeRoleFromMember(member, roleById).queue();
        }
    }

    public Guild guild() {
        return products().guild();
    }

    public NexusRest nexus() {
        return nexus;
    }
}
