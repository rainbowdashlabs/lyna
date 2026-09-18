/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.feature.license.entity.Sharee;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Who holds a licence, who it is shared with, and what it opens.
 *
 * <p>Statements only. What a claim means for somebody's Discord roles is the service's business.
 */
@Singleton
public class LicenseRepository {

    /**
     * @return the Discord id of whoever holds it, or nothing when nobody does or the holder has no
     *         Discord identity
     */
    public Optional<Long> ownerDiscordId(int licenseId) {
        return query("""
                SELECT i.external_id::BIGINT AS user_id
                FROM user_license u
                    JOIN account_identity i
                        ON i.account_id = u.account_id AND i.provider = 'discord'
                WHERE u.license_id = ?
                """)
                .single(call().bind(licenseId))
                .map(row -> row.getLong("user_id"))
                .first();
    }

    /**
     * @return the Discord ids of the sharees, which is what the role logic can act on
     */
    public List<Long> shareeDiscordIds(int licenseId) {
        return query("""
                SELECT i.external_id::BIGINT AS user_id
                FROM user_sub_license u
                    JOIN account_identity i
                        ON i.account_id = u.account_id AND i.provider = 'discord'
                WHERE u.license_id = ?
                """)
                .single(call().bind(licenseId))
                .map(row -> row.getLong("user_id"))
                .all();
    }

    /**
     * Everybody the licence is shared with, named.
     *
     * <p>Unlike {@link #shareeDiscordIds}, this counts the people who hold the licence through the
     * web and have no Discord id at all. The role logic wants the former; anything that reports to a
     * person wants this, or it quietly says a licence is shared with fewer people than it is.
     *
     * @return the sharees, those with a Discord id first
     */
    public List<Sharee> sharees(int licenseId) {
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
                .single(call().bind(licenseId))
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
    public int shareCount(int licenseId) {
        return query("""
                SELECT (SELECT count(*) FROM user_sub_license WHERE license_id = ?)
                     + (SELECT count(*) FROM license_invite
                        WHERE license_id = ? AND expires_at > now()) AS used
                """)
                .single(call().bind(licenseId).bind(licenseId))
                .map(row -> row.getInt("used"))
                .first()
                .orElse(0);
    }

    public boolean delete(int licenseId) {
        return query("DELETE FROM license WHERE id = ?")
                .single(call().bind(licenseId))
                .delete()
                .changed();
    }

    /**
     * Gives the licence to an account, unless somebody already holds it.
     */
    public boolean claim(int accountId, int licenseId) {
        return query("INSERT INTO user_license(account_id, license_id) VALUES(?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(accountId).bind(licenseId))
                .insert()
                .changed();
    }

    /**
     * Moves the licence to an account, whoever held it before.
     */
    public boolean transfer(int accountId, int licenseId) {
        return query(
                        "INSERT INTO user_license(account_id, license_id) VALUES(?,?) ON CONFLICT(license_id) DO UPDATE SET account_id = excluded.account_id")
                .single(call().bind(accountId).bind(licenseId))
                .insert()
                .changed();
    }

    public void clearSharees(int licenseId) {
        query("DELETE FROM user_sub_license WHERE license_id = ?")
                .single(call().bind(licenseId))
                .delete();
    }

    public boolean removeSharee(int licenseId, int accountId) {
        return query("DELETE FROM user_sub_license WHERE license_id = ? AND account_id = ?")
                .single(call().bind(licenseId).bind(accountId))
                .delete()
                .changed();
    }

    public boolean addSharee(int licenseId, int accountId) {
        return query("INSERT INTO user_sub_license(account_id, license_id) VALUES (?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(accountId).bind(licenseId))
                .insert()
                .changed();
    }

    public boolean grantAccess(int licenseId, ReleaseType type) {
        return query(
                        "INSERT INTO license_access(license_id, release_type) VALUES (?,?::RELEASE_TYPE) ON CONFLICT DO NOTHING")
                .single(call().bind(licenseId).bind(type))
                .insert()
                .changed();
    }

    public List<ReleaseType> access(int licenseId) {
        return query("SELECT release_type FROM license_access WHERE license_id = ?")
                .single(call().bind(licenseId))
                .map(row -> row.getEnum("release_type", ReleaseType.class))
                .all();
    }
}
