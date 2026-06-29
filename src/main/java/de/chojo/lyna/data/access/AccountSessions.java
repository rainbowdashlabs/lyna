package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.AccountSession;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class AccountSessions {

    public void record(String jti, int accountId, Instant expiresAt, String userAgent) {
        query("""
                INSERT INTO account_session (jti, account_id, expires_at, user_agent)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (jti) DO NOTHING
                """)
                .single(call()
                        .bind(jti)
                        .bind(accountId)
                        .bind(Timestamp.from(expiresAt))
                        .bind(userAgent))
                .insert();
    }

    public Optional<AccountSession> find(String jti) {
        return query("""
                SELECT jti, account_id, issued_at, expires_at, last_seen_at, user_agent
                FROM account_session WHERE jti = ?
                """)
                .single(call().bind(jti))
                .map(row -> new AccountSession(
                        row.getString("jti"),
                        row.getInt("account_id"),
                        toInstant(row.getTimestamp("issued_at")),
                        toInstant(row.getTimestamp("expires_at")),
                        toInstant(row.getTimestamp("last_seen_at")),
                        row.getString("user_agent")))
                .first();
    }

    public List<AccountSession> activeForAccount(int accountId) {
        return query("""
                SELECT s.jti, s.account_id, s.issued_at, s.expires_at, s.last_seen_at, s.user_agent
                FROM account_session s
                LEFT JOIN revoked_jti r ON r.jti = s.jti
                WHERE s.account_id = ?
                  AND s.expires_at > now()
                  AND r.jti IS NULL
                ORDER BY s.issued_at DESC
                """)
                .single(call().bind(accountId))
                .map(row -> new AccountSession(
                        row.getString("jti"),
                        row.getInt("account_id"),
                        toInstant(row.getTimestamp("issued_at")),
                        toInstant(row.getTimestamp("expires_at")),
                        toInstant(row.getTimestamp("last_seen_at")),
                        row.getString("user_agent")))
                .all();
    }

    public void touchLastSeen(String jti) {
        query("UPDATE account_session SET last_seen_at = now() WHERE jti = ?")
                .single(call().bind(jti))
                .update();
    }

    public void deleteAllForAccount(int accountId) {
        query("DELETE FROM account_session WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
