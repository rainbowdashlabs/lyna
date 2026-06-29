package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.DiscordLink;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Accounts {

    public Account create(String email, String passwordHash) {
        return query("""
                INSERT INTO account (email, password_hash)
                VALUES (?, ?)
                RETURNING id, email, password_hash, theme, feel, dark_mode, created_at, last_login_at
                """)
                .single(call().bind(email).bind(passwordHash))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getString("password_hash"),
                        row.getString("theme"),
                        row.getString("feel"),
                        row.getString("dark_mode"),
                        toInstant(row.getTimestamp("created_at")),
                        toInstant(row.getTimestamp("last_login_at"))))
                .first()
                .orElseThrow(() -> new IllegalStateException("Failed to insert account"));
    }

    public Optional<Account> findById(int id) {
        return query("""
                SELECT id, email, password_hash, theme, feel, dark_mode, created_at, last_login_at
                FROM account WHERE id = ?
                """)
                .single(call().bind(id))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getString("password_hash"),
                        row.getString("theme"),
                        row.getString("feel"),
                        row.getString("dark_mode"),
                        toInstant(row.getTimestamp("created_at")),
                        toInstant(row.getTimestamp("last_login_at"))))
                .first();
    }

    public Optional<Account> findByEmail(String email) {
        return query("""
                SELECT id, email, password_hash, theme, feel, dark_mode, created_at, last_login_at
                FROM account WHERE LOWER(email) = LOWER(?)
                """)
                .single(call().bind(email))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getString("password_hash"),
                        row.getString("theme"),
                        row.getString("feel"),
                        row.getString("dark_mode"),
                        toInstant(row.getTimestamp("created_at")),
                        toInstant(row.getTimestamp("last_login_at"))))
                .first();
    }

    public Optional<Account> findByDiscordId(long discordUserId) {
        return query("""
                SELECT a.id, a.email, a.password_hash, a.theme, a.feel, a.dark_mode, a.created_at, a.last_login_at
                FROM account a
                JOIN account_discord_link l ON l.account_id = a.id
                WHERE l.discord_user_id = ?
                """)
                .single(call().bind(discordUserId))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getString("password_hash"),
                        row.getString("theme"),
                        row.getString("feel"),
                        row.getString("dark_mode"),
                        toInstant(row.getTimestamp("created_at")),
                        toInstant(row.getTimestamp("last_login_at"))))
                .first();
    }

    public Optional<DiscordLink> findLinkByAccountId(int accountId) {
        return query("""
                SELECT account_id, discord_user_id, linked_at, verified_via
                FROM account_discord_link WHERE account_id = ?
                """)
                .single(call().bind(accountId))
                .map(row -> new DiscordLink(
                        row.getInt("account_id"),
                        row.getLong("discord_user_id"),
                        toInstant(row.getTimestamp("linked_at")),
                        row.getString("verified_via")))
                .first();
    }

    public void link(int accountId, long discordUserId, DiscordLink.Verification via) {
        query("""
                INSERT INTO account_discord_link (account_id, discord_user_id, verified_via)
                VALUES (?, ?, ?)
                ON CONFLICT (account_id) DO UPDATE SET
                    discord_user_id = EXCLUDED.discord_user_id,
                    linked_at       = now(),
                    verified_via    = EXCLUDED.verified_via
                """)
                .single(call().bind(accountId).bind(discordUserId).bind(via.dbValue()))
                .insert();
    }

    public void unlink(int accountId) {
        query("DELETE FROM account_discord_link WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    public void touchLastLogin(int accountId) {
        query("UPDATE account SET last_login_at = now() WHERE id = ?")
                .single(call().bind(accountId))
                .update();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
