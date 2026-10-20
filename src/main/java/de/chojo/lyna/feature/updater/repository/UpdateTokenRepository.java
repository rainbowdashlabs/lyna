/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

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
 * The upgrade tokens accounts hand to their updaters.
 *
 * <p>A token is good for exactly one trade. {@link #rotate} replaces it and keeps the old one in
 * {@code update_token_retired}, so a copy presented later is recognised as a copy instead of being
 * mistaken for a token nobody issued.
 */
public class UpdateTokenRepository {
    private static final String SELECT = """
            SELECT t.id, t.account_id, t.product_id, p.name AS product_name, t.name, t.token, t.created_at,
                   t.last_used_at,
                   (SELECT max(r.reused_at) FROM update_token_retired r WHERE r.update_token_id = t.id) AS last_reuse_at
            FROM update_token t
            JOIN product p ON p.id = t.product_id
            """;

    /**
     * @return the entry, as it was written
     */
    public UpdateToken create(int accountId, int productId, String name, String token) {
        int id = query("""
                INSERT INTO update_token (account_id, product_id, name, token) VALUES (?, ?, ?, ?) RETURNING id
                """)
                .single(call().bind(accountId).bind(productId).bind(name).bind(token))
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
     * @return the entry whose current token this is; a retired token finds nothing
     */
    public Optional<UpdateToken> byToken(String token) {
        return query(SELECT + "WHERE t.token = ?")
                .single(call().bind(token))
                .map(UpdateTokenRepository::read)
                .first();
    }

    /**
     * @return every entry of the account, by product and then by name
     */
    public List<UpdateToken> forAccount(int accountId) {
        return query(SELECT + "WHERE t.account_id = ? ORDER BY p.name, t.name, t.id")
                .single(call().bind(accountId))
                .map(UpdateTokenRepository::read)
                .all();
    }

    public int count(int accountId, int productId) {
        return query("SELECT count(*) AS count FROM update_token WHERE account_id = ? AND product_id = ?")
                .single(call().bind(accountId).bind(productId))
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
     * Ends an entry. Its token and every one it traded in stop meaning anything.
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
     * Gives an entry a new token by the hand of its owner, for an updater that lost the one it had. The
     * old one is retired like a traded one, so a copy of it is still recognised.
     *
     * @return the entry with its new token, or empty if the account has no entry by that id
     */
    public Optional<UpdateToken> reissue(int id, int accountId, String token) {
        Optional<Integer> replaced = query("""
                WITH old AS (SELECT id, token FROM update_token WHERE id = ? AND account_id = ? FOR UPDATE),
                     updated AS (UPDATE update_token t SET token = ? FROM old WHERE t.id = old.id RETURNING t.id),
                     retired AS (INSERT INTO update_token_retired (token, update_token_id) SELECT token, id FROM old)
                SELECT id FROM updated
                """)
                .single(call().bind(id).bind(accountId).bind(token))
                .map(row -> row.getInt("id"))
                .first();
        return replaced.flatMap(this::byId);
    }

    /**
     * Trades a token for its successor and records the trade.
     *
     * <p>One statement, conditional on the token still being current: two trades of the same token
     * at the same moment cannot both succeed, because the second finds it already replaced.
     *
     * @return the entry with its new token, or empty if the token was not current
     */
    public Optional<UpdateToken> rotate(String token, String successor) {
        Optional<Integer> rotated = query("""
                WITH updated AS (
                    UPDATE update_token SET token = ?, last_used_at = now() WHERE token = ? RETURNING id
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
     * Notes that a token already traded in was presented again.
     *
     * @return the entry it belonged to, or empty for a token nobody ever issued
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

    private static UpdateToken read(Row row) throws SQLException {
        return new UpdateToken(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getInt("product_id"),
                row.getString("product_name"),
                row.getString("name"),
                row.getString("token"),
                toInstant(row.getTimestamp("created_at")),
                toInstant(row.getTimestamp("last_used_at")),
                toInstant(row.getTimestamp("last_reuse_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
