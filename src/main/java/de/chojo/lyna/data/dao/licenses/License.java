/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.dao.licenses;

import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Member;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;
import static org.slf4j.LoggerFactory.getLogger;

public class License {
    private static final Logger log = getLogger(License.class);
    /**
     * The product the license is for.
     */
    private final Product product;

    /**
     * Identifier of the user.
     */
    private final String userIdentifier;

    /**
     * License id
     */
    private final int id;

    /**
     * License key
     */
    private final String key;

    /**
     * The owner owning this license.
     */
    private long owner;

    /**
     * The sub users of the license.
     */
    private final List<Long> subUsers;

    public License(Product product, String userIdentifier, int id, String key) {
        this(product, userIdentifier, id, key, -1, new ArrayList<>());
    }

    public License(Product product, String userIdentifier, int id, String key, long owner, List<Long> subUsers) {
        this.product = product;
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
        owner = query("""
                SELECT i.external_id::BIGINT AS user_id
                FROM user_license u
                    JOIN account_identity i
                        ON i.account_id = u.account_id AND i.provider = 'discord'
                WHERE u.license_id = ?
                """)
                .single(call().bind(id))
                .map(row -> row.getLong("user_id"))
                .first()
                .orElse(0L);
        return owner;
    }

    public List<Long> subUsers() {
        return query("""
                SELECT i.external_id::BIGINT AS user_id
                FROM user_sub_license u
                    JOIN account_identity i
                        ON i.account_id = u.account_id AND i.provider = 'discord'
                WHERE u.license_id = ?
                """)
                .single(call().bind(id))
                .map(row -> row.getLong("user_id"))
                .all();
    }

    /**
     * Everybody the licence is shared with, named.
     *
     * <p>Unlike {@link #subUsers()}, which answers only for the ones Discord can be told about, this
     * counts the people who hold the licence through the web and have no Discord id at all. The role
     * logic wants the former; anything that reports to a person wants this, or it quietly says a
     * licence is shared with fewer people than it is.
     *
     * @return the sharees, those with a Discord id first
     */
    public List<Sharee> sharees() {
        return query("""
                SELECT i.external_id AS discord_id,
                       a.username,
                       a.discriminator,
                       u.account_id
                FROM user_sub_license u
                    JOIN account a ON a.id = u.account_id
                    LEFT JOIN account_identity i
                        ON i.account_id = u.account_id AND i.provider = 'discord'
                WHERE u.license_id = ?
                ORDER BY (i.external_id IS NULL), u.account_id
                """)
                .single(call().bind(id))
                .map(row -> {
                    String discordId = row.getString("discord_id");
                    String username = row.getString("username");
                    String discriminator = row.getString("discriminator");
                    String name = username == null || username.isBlank()
                            ? "account " + row.getInt("account_id")
                            : discriminator == null ? username : username + "#" + discriminator;
                    return new Sharee(discordId == null ? null : Long.parseLong(discordId), name);
                })
                .all();
    }

    /**
     * What the guild's share cap is measured against.
     *
     * <p>An invite nobody has answered holds a place: counting only accepted shares would let
     * somebody invite the world and hand out places as the replies arrived.
     *
     * @return sharees plus invites still standing
     */
    public int shareCount() {
        return query("""
                SELECT (SELECT count(*) FROM user_sub_license WHERE license_id = ?)
                     + (SELECT count(*) FROM license_invite
                        WHERE license_id = ? AND expires_at > now()) AS used
                """)
                .single(call().bind(id).bind(id))
                .map(row -> row.getInt("used"))
                .first()
                .orElse(0);
    }

    /**
     * @param discordId the sharee's Discord id, or null for somebody who holds this through the web
     *                  alone and can therefore be given no Discord role
     * @param name      what to call them
     */
    public record Sharee(Long discordId, String name) {
        public String display() {
            return discordId == null ? name : "<@%d> (%s)".formatted(discordId, name);
        }
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

    /**
     * Takes the license away, and the product's role with it.
     *
     * <p>The owner's role goes unconditionally: the license granting it is about to stop existing,
     * so there is nothing left to weigh it against.
     *
     * <p>Through {@link #owner()} rather than the field behind it, which is {@code -1} on a license
     * read from the database until something asks - so this used to take the role from member -1.
     */
    public boolean delete() {
        clearSubUsers();
        long owner = owner();
        long guildId = product.products().licenseGuild().guildId();
        product.products().licenseGuild().roles().revoke(guildId, owner, product);
        return query("DELETE FROM license WHERE id = ?")
                .single(call().bind(id))
                .delete()
                .changed();
    }

    public boolean claim(Member member) {
        if (query("INSERT INTO user_license(account_id, license_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(Accounts.accountIdForDiscord(member.getIdLong()))
                        .bind(id))
                .insert()
                .changed()) {
            log.info(
                    LogNotify.STATUS,
                    "{} claimed license {} for {}",
                    member.getEffectiveName(),
                    id,
                    product().name());
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
        if (query(
                        "INSERT INTO user_license(account_id, license_id) VALUES(?,?) ON CONFLICT(license_id) DO UPDATE SET account_id = excluded.account_id")
                .single(call().bind(Accounts.accountIdForDiscord(member.getIdLong()))
                        .bind(id))
                .insert()
                .changed()) {
            Member oldOwner = member.getGuild().retrieveMemberById(owner).complete();
            if (oldOwner != null && !product.canAccess(oldOwner)) {
                log.info(
                        LogNotify.STATUS,
                        "{} transferred license for {} to {}",
                        oldOwner.getEffectiveName(),
                        product.name(),
                        member.getEffectiveName());
                product.revoke(oldOwner);
            }
            owner = member.getIdLong();
            product.assign(member);
            return true;
        }
        return false;
    }

    /**
     * Ends every share of this license, and takes the product's role back from anybody it was the
     * only thing granting.
     *
     * <p>The rows go first and the entitlement is weighed afterwards. Asked the other way round -
     * which is how this used to read - every sharee still held the share being cleared, so every one
     * of them answered "entitled" and nobody ever lost the role.
     */
    public void clearSubUsers() {
        List<Long> sharees = subUsers();

        query("DELETE FROM user_sub_license WHERE license_id = ?")
                .single(call().bind(id()))
                .delete();

        long guildId = product.products().licenseGuild().guildId();
        for (Long sharee : sharees) {
            product.products().licenseGuild().roles().revokeIfUnentitled(guildId, sharee, product);
        }
    }

    public boolean removeSubUser(Member member) {
        boolean changed = query("DELETE FROM user_sub_license WHERE license_id = ? AND account_id = ?")
                .single(call().bind(id()).bind(Accounts.accountIdForDiscord(member.getIdLong())))
                .delete()
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
        log.info(
                LogNotify.STATUS, "{} shared license for {} with {}", owner, product.name(), member.getEffectiveName());
        return query("INSERT INTO user_sub_license(account_id, license_id) VALUES (?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(Accounts.accountIdForDiscord(member.getIdLong()))
                        .bind(id()))
                .insert()
                .changed();
    }

    public boolean grantAccess(ReleaseType type) {
        return query(
                        "INSERT INTO license_access(license_id, release_type) VALUES (?,?::RELEASE_TYPE) ON CONFLICT DO NOTHING")
                .single(call().bind(id).bind(type))
                .insert()
                .changed();
    }

    public List<ReleaseType> access() {
        return query("SELECT release_type FROM license_access WHERE license_id = ?")
                .single(call().bind(id))
                .map(row -> row.getEnum("release_type", ReleaseType.class))
                .all();
    }
}
