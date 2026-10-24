/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

import de.chojo.lyna.feature.updater.entity.HeldKey;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.RetiredKey;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The keys accounts hand to their servers: upgrade tokens for updaters and dashboard keys for the
 * hosted dashboard.
 *
 * <p>A key is replaced by {@link #rotate}, and the old one is kept in {@code update_token_retired}, so
 * a copy presented later is recognised as a copy instead of being mistaken for a key nobody issued.
 *
 * <p>When a dashboard key is due for its successor and when it expires is worked out here, from the
 * product's settings, so every reader sees the same answer. A day there is 24 hours rather than a
 * calendar day, so a change of daylight saving time moves no key's schedule by an hour.
 */
public class UpdateTokenRepository {
    private static final String SELECT = """
            SELECT t.id, t.account_id, t.product_id, p.name AS product_name, t.kind, t.name, t.token, t.created_at,
                   t.last_used_at, t.rotated_at, t.refreshed_at,
                   (SELECT max(r.reused_at) FROM update_token_retired r WHERE r.update_token_id = t.id) AS last_reuse_at,
                   CASE WHEN t.kind = 'DASHBOARD'
                        THEN coalesce(t.rotated_at, t.created_at) + p.dashboard_refresh_days * interval '24 hours'
                   END AS refresh_at,
                   CASE WHEN t.kind = 'DASHBOARD'
                        THEN greatest(t.last_used_at, t.rotated_at, t.created_at)
                             + p.dashboard_expiry_days * interval '24 hours'
                   END AS expires_at
            FROM update_token t
            JOIN product p ON p.id = t.product_id
            """;

    /**
     * @return the entry, as it was written
     */
    public UpdateToken create(KeyKind kind, int accountId, int productId, String name, String token) {
        int id = query("""
                INSERT INTO update_token (account_id, product_id, kind, name, token, rotated_at)
                VALUES (?, ?, ?, ?, ?, now())
                RETURNING id
                """)
                .single(call().bind(accountId)
                        .bind(productId)
                        .bind(kind.name())
                        .bind(name)
                        .bind(token))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
        return byId(id).orElseThrow();
    }

    public Optional<UpdateToken> byId(int id) {
        return query(SELECT + "WHERE t.id = ?")
                .single(call().bind(id))
                .map(UpdateTokenRepository::read)
                .first();
    }

    /**
     * @return the entry whose current key this is; a retired key finds nothing
     */
    public Optional<UpdateToken> byToken(String token) {
        return query(SELECT + "WHERE t.token = ?")
                .single(call().bind(token))
                .map(UpdateTokenRepository::read)
                .first();
    }

    /**
     * @return every entry of the account, by product, kind and then by name
     */
    public List<UpdateToken> forAccount(int accountId) {
        return query(SELECT + "WHERE t.account_id = ? ORDER BY p.name, t.kind DESC, t.name, t.id")
                .single(call().bind(accountId))
                .map(UpdateTokenRepository::read)
                .all();
    }

    public int count(int accountId, int productId, KeyKind kind) {
        return query("""
                SELECT count(*) AS count FROM update_token WHERE account_id = ? AND product_id = ? AND kind = ?
                """)
                .single(call().bind(accountId).bind(productId).bind(kind.name()))
                .map(row -> row.getInt("count"))
                .first()
                .orElse(0);
    }

    /**
     * @return whether the account has an entry by that id to rename
     */
    public boolean rename(int id, int accountId, String name) {
        return query("UPDATE update_token SET name = ? WHERE id = ? AND account_id = ?")
                .single(call().bind(name).bind(id).bind(accountId))
                .update()
                .changed();
    }

    /**
     * Ends an entry. Its key and every one it gave up stop meaning anything.
     *
     * @return whether the account had an entry by that id
     */
    public boolean delete(int id, int accountId) {
        return query("DELETE FROM update_token WHERE id = ? AND account_id = ?")
                .single(call().bind(id).bind(accountId))
                .delete()
                .changed();
    }

    /**
     * Gives an entry a new key by the hand of its owner, for a server that lost the one it had or let it
     * expire. The old one is retired like a traded one, so a copy of it is still recognised.
     *
     * @return the entry with its new key, or empty if the account has no entry by that id
     */
    public Optional<UpdateToken> reissue(int id, int accountId, String token) {
        Optional<Integer> replaced = query("""
                WITH old AS (SELECT id, token FROM update_token WHERE id = ? AND account_id = ? FOR UPDATE),
                     updated AS (
                         UPDATE update_token t SET token = ?, rotated_at = now() FROM old WHERE t.id = old.id
                         RETURNING t.id
                     ),
                     retired AS (INSERT INTO update_token_retired (token, update_token_id) SELECT token, id FROM old)
                SELECT id FROM updated
                """)
                .single(call().bind(id).bind(accountId).bind(token))
                .map(row -> row.getInt("id"))
                .first();
        return replaced.flatMap(this::byId);
    }

    /**
     * Trades a key for its successor and records the trade. A dashboard key traded this way was
     * refreshed, which is noted too.
     *
     * <p>One statement, conditional on the key still being current: two trades of the same key at the
     * same moment cannot both succeed, because the second finds it already replaced.
     *
     * @return the entry with its new key, or empty if the key was not current
     */
    public Optional<UpdateToken> rotate(String token, String successor) {
        Optional<Integer> rotated = query("""
                WITH updated AS (
                    UPDATE update_token
                    SET token = ?, last_used_at = now(), rotated_at = now(),
                        refreshed_at = CASE WHEN kind = 'DASHBOARD' THEN now() ELSE refreshed_at END
                    WHERE token = ?
                    RETURNING id
                ),
                retired AS (
                    INSERT INTO update_token_retired (token, update_token_id) SELECT ?, id FROM updated
                )
                SELECT id FROM updated
                """)
                .single(call().bind(successor).bind(token).bind(token))
                .map(row -> row.getInt("id"))
                .first();
        return rotated.flatMap(this::byId);
    }

    /**
     * Notes that a key was used without replacing it.
     *
     * @param refreshed whether its server asked to refresh it, rather than the gateway verifying it
     * @return the entry as it is now, or empty if there is none by that id
     */
    public Optional<UpdateToken> touch(int id, boolean refreshed) {
        query("""
                UPDATE update_token
                SET last_used_at = now(), refreshed_at = CASE WHEN ? THEN now() ELSE refreshed_at END
                WHERE id = ?
                """).single(call().bind(refreshed).bind(id)).update();
        return byId(id);
    }

    /**
     * Looks a key up among those given up, without noting that it was presented.
     *
     * @return the key's entry and when it was given up, or empty for a key that never was
     */
    public Optional<RetiredKey> retired(String token) {
        return query("""
                SELECT r.update_token_id, r.retired_at, t.kind
                FROM update_token_retired r
                JOIN update_token t ON t.id = r.update_token_id
                WHERE r.token = ?
                """)
                .single(call().bind(token))
                .map(row -> new RetiredKey(
                        row.getInt("update_token_id"),
                        KeyKind.valueOf(row.getString("kind")),
                        toInstant(row.getTimestamp("retired_at"))))
                .first();
    }

    /**
     * Notes that a key already given up was presented again.
     *
     * @return the entry it belonged to, or empty for a key nobody ever issued
     */
    public Optional<UpdateToken> markReused(String token) {
        Optional<Integer> owner = query("""
                UPDATE update_token_retired SET reused_at = now() WHERE token = ? RETURNING update_token_id
                """)
                .single(call().bind(token))
                .map(row -> row.getInt("update_token_id"))
                .first();
        return owner.flatMap(this::byId);
    }

    /**
     * @return the keys of one kind held for the guild's products, by product, account and name
     */
    public List<HeldKey> inGuild(long guildId, KeyKind kind) {
        return query("""
                SELECT t.id, t.account_id, t.product_id, p.name AS product_name, t.kind, t.name, t.created_at,
                       t.last_used_at,
                       CASE WHEN t.kind = 'DASHBOARD'
                            THEN greatest(t.last_used_at, t.rotated_at, t.created_at)
                                 + p.dashboard_expiry_days * interval '24 hours'
                       END AS expires_at,
                       coalesce(CASE WHEN a.discriminator IS NULL THEN a.username
                                     ELSE a.username || '#' || a.discriminator END,
                                'account ' || a.id) AS account
                FROM update_token t
                JOIN product p ON p.id = t.product_id
                JOIN account a ON a.id = t.account_id
                WHERE p.guild_id = ? AND t.kind = ?
                ORDER BY p.name, account, t.name, t.id
                """)
                .single(call().bind(guildId).bind(kind.name()))
                .map(row -> new HeldKey(
                        row.getInt("id"),
                        row.getInt("account_id"),
                        row.getString("account"),
                        row.getInt("product_id"),
                        row.getString("product_name"),
                        KeyKind.valueOf(row.getString("kind")),
                        row.getString("name"),
                        toInstant(row.getTimestamp("created_at")),
                        toInstant(row.getTimestamp("last_used_at")),
                        toInstant(row.getTimestamp("expires_at"))))
                .all();
    }

    /**
     * Ends an entry of one kind on a product of the guild, whoever holds it.
     *
     * @return whether there was such an entry
     */
    public boolean deleteInGuild(long guildId, int id, KeyKind kind) {
        return query("""
                DELETE FROM update_token t USING product p
                WHERE t.id = ? AND t.kind = ? AND p.id = t.product_id AND p.guild_id = ?
                """)
                .single(call().bind(id).bind(kind.name()).bind(guildId))
                .delete()
                .changed();
    }

    private static UpdateToken read(Row row) throws SQLException {
        return new UpdateToken(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getInt("product_id"),
                row.getString("product_name"),
                KeyKind.valueOf(row.getString("kind")),
                row.getString("name"),
                row.getString("token"),
                toInstant(row.getTimestamp("created_at")),
                toInstant(row.getTimestamp("last_used_at")),
                toInstant(row.getTimestamp("last_reuse_at")),
                toInstant(row.getTimestamp("rotated_at")),
                toInstant(row.getTimestamp("refreshed_at")),
                toInstant(row.getTimestamp("refresh_at")),
                toInstant(row.getTimestamp("expires_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
