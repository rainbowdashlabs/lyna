package de.chojo.lyna.data.dao.licenses;

import de.chojo.lyna.data.dao.platforms.Platform;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Member;

import java.util.ArrayList;
import java.util.List;

import static de.chojo.lyna.data.StaticQueryAdapter.builder;

public class License {
    /**
     * The product the license is for.
     */
    Product product;

    /**
     * Information about the platform and license.
     */
    Platform platform;

    /**
     * Identifier of the user on the platform.
     */
    String userIdentifier;

    /**
     * License id
     */
    int id;

    /**
     * License key
     */
    String key;

    /**
     * The owner owning this license.
     */
    long owner;

    /**
     * The sub users of the license.
     */
    List<Long> subUsers;

    public License(Product product, Platform platform, String userIdentifier, int id, String key) {
        this(product, platform, userIdentifier, id, key, -1, new ArrayList<>());
    }

    public License(Product product, Platform platform, String userIdentifier, int id, String key, long owner, List<Long> subUsers) {
        this.product = product;
        this.platform = platform;
        this.userIdentifier = userIdentifier;
        this.id = id;
        this.key = key;
        this.owner = owner;
        this.subUsers = subUsers;
    }

    public long owner() {
        if (owner != -1) {
            return owner;
        }
        owner = builder(Long.class)
                .query("SELECT user_id, license_id FROM user_license WHERE license_id = ?")
                .parameter(stmt -> stmt.setInt(id))
                .readRow(row -> row.getLong("user_id"))
                .firstSync().orElse(0L);
        return owner;
    }

    public List<Long> subUsers() {
        return builder(Long.class)
                .query("SELECT user_id, license_id FROM user_sub_license WHERE license_id = ?")
                .parameter(stmt -> stmt.setInt(id))
                .readRow(row -> row.getLong("user_id"))
                .allSync();
    }

    public String userIdentifier() {
        return userIdentifier;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    public Product product() {
        return product;
    }

    public Platform platform() {
        return platform;
    }

    public boolean delete() {
        clearSubUsers();
        Member complete = product.guild().retrieveMemberById(owner).complete();
        if (complete != null) {
            product.revoke(complete);
        }
        return builder()
                .query("DELETE FROM license WHERE id = ?")
                .parameter(stmt -> stmt.setInt(id))
                .delete()
                .sendSync()
                .changed();
    }

    public boolean claim(Member member) {
        if (builder()
                .query("INSERT INTO user_license(user_id, license_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .parameter(stmt -> stmt.setLong(member.getIdLong()).setInt(id))
                .insert()
                .sendSync()
                .changed()) {
            owner = member.getIdLong();
            product.assign(member);
            return true;
        }
        return false;
    }

    public boolean isClaimed() {
        return owner() != 0;
    }

    public boolean transfer(Member member) {
        clearSubUsers();
        if (builder()
                .query("INSERT INTO user_license(user_id, license_id) VALUES(?,?) ON CONFLICT(license_id) DO UPDATE SET user_id = excluded.user_id")
                .parameter(stmt -> stmt.setLong(member.getIdLong()).setInt(id))
                .insert()
                .sendSync()
                .changed()) {
            Member oldOwner = member.getGuild().retrieveMemberById(owner).complete();
            if (oldOwner != null && !product.canAccess(oldOwner)) {
                product.revoke(oldOwner);
            }
            owner = member.getIdLong();
            product.assign(member);
            return true;
        }
        return false;
    }

    public void clearSubUsers() {
        for (Long subUser : subUsers()) {
            Member complete = product.guild().retrieveMemberById(subUser).complete();
            if (complete != null && !product.canAccess(complete)) {
                product.revoke(complete);
            }
        }

        builder()
                .query("DELETE FROM user_sub_license WHERE license_id = ?")
                .parameter(stmt -> stmt.setInt(id()))
                .delete()
                .sendSync();
    }

    public boolean removeSubUser(Member member) {
        boolean changed = builder()
                .query("DELETE FROM user_sub_license WHERE license_id = ? AND user_id = ?")
                .parameter(stmt -> stmt.setInt(id()).setLong(member.getIdLong()))
                .delete()
                .sendSync()
                .changed();
        if (changed) {
            if (!product.canAccess(member)) {
                product.revoke(member);
            }
        }
        return changed;
    }

    public boolean addSubUser(Member member) {
        product.assign(member);
        return builder()
                .query("INSERT INTO user_sub_license(user_id, license_id) VALUES (?,?) ON CONFLICT DO NOTHING")
                .parameter(stmt -> stmt.setLong(member.getIdLong()).setInt(id()))
                .insert()
                .sendSync()
                .changed();
    }
}
