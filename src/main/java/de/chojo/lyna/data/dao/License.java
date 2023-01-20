package de.chojo.lyna.data.dao;

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
            return true;
        }
        return false;
    }

    public boolean isClaimed() {
        return owner() != 0;
    }
}
