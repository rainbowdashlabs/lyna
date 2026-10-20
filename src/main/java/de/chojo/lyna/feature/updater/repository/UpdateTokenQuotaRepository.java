/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * How many upgrade tokens an account was allowed beyond the default, and the requests for more.
 */
public class UpdateTokenQuotaRepository {
    private static final String SELECT_REQUEST = """
            SELECT r.id, r.account_id, r.product_id, p.name AS product_name, r.amount, r.reason, r.status,
                   r.created_at, r.decided_at,
                   coalesce(CASE WHEN a.discriminator IS NULL THEN a.username
                                 ELSE a.username || '#' || a.discriminator END,
                            'account ' || a.id) AS account
            FROM update_token_request r
            JOIN product p ON p.id = r.product_id
            JOIN account a ON a.id = r.account_id
            """;

    /**
     * @return the tokens allowed on top of the default, zero when nobody granted any
     */
    public int granted(int accountId, int productId) {
        return query("SELECT granted FROM update_token_quota WHERE account_id = ? AND product_id = ?")
                .single(call().bind(accountId).bind(productId))
                .map(row -> row.getInt("granted"))
                .first()
                .orElse(0);
    }

    /**
     * Sets the tokens allowed on top of the default. Lowering it ends no entry: an account above its
     * allowance keeps what it has and cannot add more.
     */
    public void setGranted(int accountId, int productId, int granted) {
        query("""
                INSERT INTO update_token_quota (account_id, product_id, granted) VALUES (?, ?, ?)
                ON CONFLICT (account_id, product_id) DO UPDATE SET granted = EXCLUDED.granted
                """)
                .single(call().bind(accountId).bind(productId).bind(Math.max(0, granted)))
                .insert();
    }

    /**
     * @return the request, or empty when the account is still waiting on one for this product
     */
    public Optional<UpdateTokenRequest> request(int accountId, int productId, int amount, String reason) {
        Optional<Integer> id = query("""
                INSERT INTO update_token_request (account_id, product_id, amount, reason) VALUES (?, ?, ?, ?)
                ON CONFLICT (account_id, product_id) WHERE status = 'PENDING' DO NOTHING
                RETURNING id
                """)
                .single(call().bind(accountId).bind(productId).bind(amount).bind(reason))
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
     * @return the requests waiting on the guild's products, oldest first
     */
    public List<UpdateTokenRequest> pendingInGuild(long guildId) {
        return query(SELECT_REQUEST + "WHERE p.guild_id = ? AND r.status = 'PENDING' ORDER BY r.created_at, r.id")
                .single(call().bind(guildId))
                .map(UpdateTokenQuotaRepository::read)
                .all();
    }

    /**
     * Settles a pending request on one of the guild's products. Approving adds what it asked for to the
     * account's allowance in the same statement, so a request cannot be settled without its effect.
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
                    RETURNING r.id, r.account_id, r.product_id, r.amount
                ),
                granted AS (
                    INSERT INTO update_token_quota (account_id, product_id, granted)
                    SELECT account_id, product_id, amount FROM decided WHERE ?
                    ON CONFLICT (account_id, product_id)
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
                row.getInt("amount"),
                row.getString("reason"),
                UpdateTokenRequest.Status.valueOf(row.getString("status")),
                toInstant(row.getTimestamp("created_at")),
                toInstant(row.getTimestamp("decided_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
