/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.repository;

import de.chojo.lyna.feature.account.entity.DiscordToken;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The Discord grant each account keeps, one row per account. The tokens arrive and leave encrypted;
 * nothing here can read them.
 */
public class DiscordTokenRepository {
    private static final String COLUMNS = "account_id, access_token, refresh_token, scope, expires_at, refreshed_at";

    /**
     * Keeps a grant, replacing whatever the account held before.
     */
    public void save(int accountId, byte[] accessToken, byte[] refreshToken, String scope, Instant expiresAt) {
        query("""
                INSERT INTO discord_token (account_id, access_token, refresh_token, scope, expires_at)
                VALUES (?, ?, ?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE
                SET access_token = excluded.access_token,
                    refresh_token = excluded.refresh_token,
                    scope = excluded.scope,
                    expires_at = excluded.expires_at,
                    refreshed_at = now()
                """)
                .single(call().bind(accountId)
                        .bind(accessToken)
                        .bind(refreshToken)
                        .bind(scope)
                        .bind(Timestamp.from(expiresAt)))
                .insert();
    }

    public Optional<DiscordToken> find(int accountId) {
        return query("SELECT " + COLUMNS + " FROM discord_token WHERE account_id = ?")
                .single(call().bind(accountId))
                .map(DiscordTokenRepository::token)
                .first();
    }

    /**
     * @return every grant whose access token runs out before the given moment, soonest first
     */
    public List<DiscordToken> expiringBefore(Instant moment) {
        return query("SELECT " + COLUMNS + " FROM discord_token WHERE expires_at < ? ORDER BY expires_at")
                .single(call().bind(Timestamp.from(moment)))
                .map(DiscordTokenRepository::token)
                .all();
    }

    /**
     * @return whether there was a grant to forget
     */
    public boolean delete(int accountId) {
        return query("DELETE FROM discord_token WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete()
                .changed();
    }

    private static DiscordToken token(Row row) throws SQLException {
        return new DiscordToken(
                row.getInt("account_id"),
                row.getBytes("access_token"),
                row.getBytes("refresh_token"),
                row.getString("scope"),
                row.getTimestamp("expires_at").toInstant(),
                row.getTimestamp("refreshed_at").toInstant());
    }
}
