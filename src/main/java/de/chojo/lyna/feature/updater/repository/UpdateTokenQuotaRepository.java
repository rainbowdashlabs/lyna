/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.RaisedAllowance;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.sadu.mapper.wrapper.Row;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * How many keys of each kind an account was allowed beyond the default, and the requests for more.
 */
public class UpdateTokenQuotaRepository {
    private static final String SELECT_REQUEST = """
            SELECT r.id, r.account_id, r.product_id, p.name AS product_name, r.kind, r.key_type_id,
                   k.name AS key_type_name, r.amount, r.reason, r.status, r.created_at, r.decided_at,
                   coalesce(CASE WHEN a.discriminator IS NULL THEN a.username
                                 ELSE a.username || '#' || a.discriminator END,
                            'account ' || a.id) AS account
            FROM update_token_request r
            JOIN product p ON p.id = r.product_id
            JOIN account a ON a.id = r.account_id
            LEFT JOIN product_key_type k ON k.id = r.key_type_id
            """;

    /**
     * @param keyTypeId the key type of server keys, null for upgrade tokens
     * @return the keys of that sort allowed on top of the default, zero when nobody granted any
     */
    public int granted(int accountId, int productId, @Nullable Integer keyTypeId) {
        return query("""
                SELECT granted FROM update_token_quota
                WHERE account_id = ? AND product_id = ? AND coalesce(key_type_id, 0) = ?
                """)
                .single(call().bind(accountId).bind(productId).bind(slot(keyTypeId)))
                .map(row -> row.getInt("granted"))
                .first()
                .orElse(0);
    }

    /**
     * Sets the tokens allowed on top of the default, for a product of the guild. Lowering it ends no
     * entry: an account above its allowance keeps what it has and cannot add more.
     *
     * @param keyTypeId the key type of server keys, null for upgrade tokens; it has to be one of the
     *                  product's
     * @return whether the product belongs to the guild
     */
    public boolean setGranted(long guildId, int accountId, int productId, @Nullable Integer keyTypeId, int granted) {
        return query("""
                INSERT INTO update_token_quota (account_id, product_id, kind, key_type_id, granted)
                SELECT ?, p.id, ?, ?, ? FROM product p
                WHERE p.id = ? AND p.guild_id = ?
                  AND (?::INTEGER IS NULL OR EXISTS (
                      SELECT 1 FROM product_key_type k WHERE k.id = ? AND k.product_id = p.id))
                ON CONFLICT (account_id, product_id, (coalesce(key_type_id, 0)))
                    DO UPDATE SET granted = EXCLUDED.granted
                """)
                .single(call().bind(accountId)
                        .bind(kind(keyTypeId).name())
                        .bind(keyTypeId)
                        .bind(Math.max(0, granted))
                        .bind(productId)
                        .bind(guildId)
                        .bind(keyTypeId)
                        .bind(keyTypeId))
                .insert()
                .changed();
    }

    /**
     * @return the accounts allowed more keys of the kind than the default on the guild's products, by
     *     product and account
     */
    public List<RaisedAllowance> raisedInGuild(long guildId, KeyKind kind) {
        return query("""
                SELECT q.account_id, q.product_id, p.name AS product_name, q.kind, q.key_type_id,
                       k.name AS key_type_name, q.granted,
                       (SELECT count(*) FROM update_token t
                        WHERE t.account_id = q.account_id AND t.product_id = q.product_id
                          AND coalesce(t.key_type_id, 0) = coalesce(q.key_type_id, 0)) AS used,
                       coalesce(CASE WHEN a.discriminator IS NULL THEN a.username
                                     ELSE a.username || '#' || a.discriminator END,
                                'account ' || a.id) AS account
                FROM update_token_quota q
                JOIN product p ON p.id = q.product_id
                JOIN account a ON a.id = q.account_id
                LEFT JOIN product_key_type k ON k.id = q.key_type_id
                WHERE p.guild_id = ? AND q.kind = ? AND q.granted > 0
                ORDER BY p.name, k.name, account
                """)
                .single(call().bind(guildId).bind(kind.name()))
                .map(row -> new RaisedAllowance(
                        row.getInt("account_id"),
                        row.getString("account"),
                        row.getInt("product_id"),
                        row.getString("product_name"),
                        KeyKind.valueOf(row.getString("kind")),
                        (Integer) row.getObject("key_type_id"),
                        row.getString("key_type_name"),
                        row.getInt("granted"),
                        row.getInt("used")))
                .all();
    }

    /**
     * @param keyTypeId the key type of server keys, null for upgrade tokens
     * @return the request, or empty when the account is still waiting on one for this product and sort of
     *     key
     */
    public Optional<UpdateTokenRequest> request(
            int accountId, int productId, @Nullable Integer keyTypeId, int amount, String reason) {
        Optional<Integer> id = query("""
                INSERT INTO update_token_request (account_id, product_id, kind, key_type_id, amount, reason)
                VALUES (?, ?, ?, ?, ?, ?)
                ON CONFLICT (account_id, product_id, (coalesce(key_type_id, 0))) WHERE status = 'PENDING' DO NOTHING
                RETURNING id
                """)
                .single(call().bind(accountId)
                        .bind(productId)
                        .bind(kind(keyTypeId).name())
                        .bind(keyTypeId)
                        .bind(amount)
                        .bind(reason))
                .map(row -> row.getInt("id"))
                .first();
        return id.flatMap(this::requestById);
    }

    public Optional<UpdateTokenRequest> requestById(int id) {
        return query(SELECT_REQUEST + "WHERE r.id = ?")
                .single(call().bind(id))
                .map(UpdateTokenQuotaRepository::read)
                .first();
    }

    /**
     * @return the account's requests, newest first
     */
    public List<UpdateTokenRequest> requestsOf(int accountId) {
        return query(SELECT_REQUEST + "WHERE r.account_id = ? ORDER BY r.created_at DESC, r.id DESC")
                .single(call().bind(accountId))
                .map(UpdateTokenQuotaRepository::read)
                .all();
    }

    /**
     * @return the requests for keys of the kind waiting on the guild's products, oldest first
     */
    public List<UpdateTokenRequest> pendingInGuild(long guildId, KeyKind kind) {
        return query(SELECT_REQUEST
                        + "WHERE p.guild_id = ? AND r.kind = ? AND r.status = 'PENDING' ORDER BY r.created_at, r.id")
                .single(call().bind(guildId).bind(kind.name()))
                .map(UpdateTokenQuotaRepository::read)
                .all();
    }

    /**
     * Settles a pending request on one of the guild's products. Approving adds what it asked for to the
     * account's allowance of the kind asked for in the same statement, so a request cannot be settled
     * without its effect.
     *
     * @param decidedBy the Discord id of whoever decided, null when that is not known
     * @return the settled request, or empty when there is no pending request by that id in the guild
     */
    public Optional<UpdateTokenRequest> decide(long guildId, int requestId, boolean approve, Long decidedBy) {
        Optional<Integer> id = query("""
                WITH decided AS (
                    UPDATE update_token_request r SET status = ?, decided_at = now(), decided_by = ?
                    FROM product p
                    WHERE r.id = ? AND r.status = 'PENDING' AND p.id = r.product_id AND p.guild_id = ?
                    RETURNING r.id, r.account_id, r.product_id, r.kind, r.key_type_id, r.amount
                ),
                granted AS (
                    INSERT INTO update_token_quota (account_id, product_id, kind, key_type_id, granted)
                    SELECT account_id, product_id, kind, key_type_id, amount FROM decided WHERE ?
                    ON CONFLICT (account_id, product_id, (coalesce(key_type_id, 0)))
                        DO UPDATE SET granted = update_token_quota.granted + EXCLUDED.granted
                )
                SELECT id FROM decided
                """)
                .single(call().bind(approve ? "APPROVED" : "DECLINED")
                        .bind(decidedBy)
                        .bind(requestId)
                        .bind(guildId)
                        .bind(approve))
                .map(row -> row.getInt("id"))
                .first();
        return id.flatMap(this::requestById);
    }

    private static UpdateTokenRequest read(Row row) throws SQLException {
        return new UpdateTokenRequest(
                row.getInt("id"),
                row.getInt("account_id"),
                row.getString("account"),
                row.getInt("product_id"),
                row.getString("product_name"),
                KeyKind.valueOf(row.getString("kind")),
                (Integer) row.getObject("key_type_id"),
                row.getString("key_type_name"),
                row.getInt("amount"),
                row.getString("reason"),
                UpdateTokenRequest.Status.valueOf(row.getString("status")),
                toInstant(row.getTimestamp("created_at")),
                toInstant(row.getTimestamp("decided_at")));
    }

    private static int slot(@Nullable Integer keyTypeId) {
        return keyTypeId == null ? 0 : keyTypeId;
    }

    private static KeyKind kind(@Nullable Integer keyTypeId) {
        return keyTypeId == null ? KeyKind.UPDATE : KeyKind.SERVER;
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
