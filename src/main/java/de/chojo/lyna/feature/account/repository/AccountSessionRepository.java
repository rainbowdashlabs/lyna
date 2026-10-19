/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.repository;

import de.chojo.lyna.feature.account.entity.AccountSession;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The sessions accounts are signed in with, one row per device.
 *
 * <p>A session is live while it is neither revoked nor past its {@code expires_at}. Everything that
 * asks whether a token may still be used asks that of the row, not of the token.
 */
public class AccountSessionRepository {
    private static final String COLUMNS =
            "jti, account_id, issued_at, expires_at, last_seen_at, user_agent, revoked_at";

    public void record(String jti, int accountId, Instant expiresAt, String userAgent) {
        query("""
                INSERT INTO account_session (jti, account_id, expires_at, user_agent)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (jti) DO NOTHING
                """)
                .single(call().bind(jti)
                        .bind(accountId)
                        .bind(Timestamp.from(expiresAt))
                        .bind(userAgent))
                .insert();
    }

    public Optional<AccountSession> find(String jti) {
        return query("SELECT " + COLUMNS + " FROM account_session WHERE jti = ?")
                .single(call().bind(jti))
                .map(AccountSessionRepository::session)
                .first();
    }

    public List<AccountSession> activeForAccount(int accountId) {
        return query("SELECT " + COLUMNS + """
                 FROM account_session
                WHERE account_id = ?
                  AND expires_at > now()
                  AND revoked_at IS NULL
                ORDER BY issued_at DESC
                """)
                .single(call().bind(accountId))
                .map(AccountSessionRepository::session)
                .all();
    }

    /**
     * Asks whether a session may still be used, and notes that it was.
     *
     * <p>One statement for both, since every authenticated request asks. The note is written at most
     * once a minute, so a page firing a dozen requests does not write a dozen times.
     *
     * @return whether the session exists, is not revoked and has not expired
     */
    public boolean touchIfLive(String jti) {
        return query("""
                WITH live AS (
                    SELECT jti FROM account_session
                    WHERE jti = ? AND revoked_at IS NULL AND expires_at > now()
                ), touched AS (
                    UPDATE account_session s SET last_seen_at = now()
                    FROM live
                    WHERE s.jti = live.jti
                      AND (s.last_seen_at IS NULL OR s.last_seen_at < now() - INTERVAL '1 minute')
                    RETURNING s.jti
                )
                SELECT EXISTS (SELECT 1 FROM live) AS live
                """)
                .single(call().bind(jti))
                .map(row -> row.getBoolean("live"))
                .first()
                .orElse(false);
    }

    /**
     * Moves a live session's expiry, which is what keeps a device in use signed in.
     *
     * @return whether there was a live session to renew
     */
    public boolean renew(String jti, Instant expiresAt) {
        return query("""
                UPDATE account_session SET expires_at = ?
                WHERE jti = ? AND revoked_at IS NULL AND expires_at > now()
                """)
                .single(call().bind(Timestamp.from(expiresAt)).bind(jti))
                .update()
                .changed();
    }

    /**
     * Ends one session, and with it every token it was issued.
     *
     * @return whether it was still open
     */
    public boolean revoke(String jti) {
        return query("UPDATE account_session SET revoked_at = now() WHERE jti = ? AND revoked_at IS NULL")
                .single(call().bind(jti))
                .update()
                .changed();
    }

    /**
     * Ends every session of an account but the one named.
     *
     * @return how many were ended
     */
    public int revokeOthers(int accountId, String keepJti) {
        return query("""
                UPDATE account_session SET revoked_at = now()
                WHERE account_id = ? AND jti <> ? AND revoked_at IS NULL
                """).single(call().bind(accountId).bind(keepJti)).update().rows();
    }

    public void deleteAllForAccount(int accountId) {
        query("DELETE FROM account_session WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    private static AccountSession session(Row row) throws SQLException {
        return new AccountSession(
                row.getString("jti"),
                row.getInt("account_id"),
                toInstant(row.getTimestamp("issued_at")),
                toInstant(row.getTimestamp("expires_at")),
                toInstant(row.getTimestamp("last_seen_at")),
                row.getString("user_agent"),
                toInstant(row.getTimestamp("revoked_at")));
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
