package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.DiscordLink;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.sql.Timestamp;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Accounts {

    public Account create(String email, String passwordHash) {
        return query("""
                INSERT INTO account (email, password_hash)
                VALUES (?, ?)
                RETURNING id, email, email_verified, password_hash, theme, feel, dark_mode, created_at, last_login_at
                """)
                .single(call().bind(email).bind(passwordHash))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getBoolean("email_verified"),
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
                SELECT id, email, email_verified, password_hash, theme, feel, dark_mode, created_at, last_login_at
                FROM account WHERE id = ?
                """)
                .single(call().bind(id))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getBoolean("email_verified"),
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
                SELECT id, email, email_verified, password_hash, theme, feel, dark_mode, created_at, last_login_at
                FROM account WHERE LOWER(email) = LOWER(?)
                """)
                .single(call().bind(email))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getBoolean("email_verified"),
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
                SELECT a.id, a.email, a.email_verified, a.password_hash, a.theme, a.feel, a.dark_mode, a.created_at, a.last_login_at
                FROM account a
                JOIN account_discord_link l ON l.account_id = a.id
                WHERE l.discord_user_id = ?
                """)
                .single(call().bind(discordUserId))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getBoolean("email_verified"),
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
                SELECT account_id, discord_user_id, linked_at, verified_via, handle
                FROM account_discord_link WHERE account_id = ?
                """)
                .single(call().bind(accountId))
                .map(row -> new DiscordLink(
                        row.getInt("account_id"),
                        row.getLong("discord_user_id"),
                        toInstant(row.getTimestamp("linked_at")),
                        row.getString("verified_via"),
                        row.getString("handle")))
                .first();
    }

    public void link(int accountId, long discordUserId, DiscordLink.Verification via) {
        link(accountId, discordUserId, via, null);
    }

    /**
     * Links an account to a Discord id, and records what that id is called if we were told.
     *
     * <p>A link made without a handle keeps whatever one was recorded before rather than clearing
     * it: the bot-DM path never learns a name, and losing the one the OAuth round trip found would
     * put the pages back to showing numbers.
     */
    public void link(int accountId, long discordUserId, DiscordLink.Verification via, String handle) {
        query("""
                INSERT INTO account_discord_link (account_id, discord_user_id, verified_via, handle, handle_seen_at)
                VALUES (?, ?, ?, ?, CASE WHEN ?::TEXT IS NULL THEN NULL ELSE now() END)
                ON CONFLICT (account_id) DO UPDATE SET
                    discord_user_id = EXCLUDED.discord_user_id,
                    linked_at       = now(),
                    verified_via    = EXCLUDED.verified_via,
                    handle          = COALESCE(EXCLUDED.handle, account_discord_link.handle),
                    handle_seen_at  = COALESCE(EXCLUDED.handle_seen_at, account_discord_link.handle_seen_at)
                """)
                .single(call().bind(accountId).bind(discordUserId).bind(via.dbValue()).bind(handle).bind(handle))
                .insert();
    }

    /**
     * Records what a Discord id is called, wherever we happen to learn it.
     *
     * <p>Keyed by the id rather than the account, because the bot meets people by id and does not
     * know which account, if any, they hold.
     *
     * @return whether a link for that id was there to update
     */
    public boolean rememberHandle(long discordUserId, String handle) {
        if (handle == null || handle.isBlank()) return false;
        return query("""
                UPDATE account_discord_link SET handle = ?, handle_seen_at = now()
                WHERE discord_user_id = ? AND (handle IS DISTINCT FROM ?)
                """)
                .single(call().bind(handle).bind(discordUserId).bind(handle))
                .update()
                .changed();
    }

    /**
     * What a set of Discord ids are called, for a page naming several people at once.
     *
     * @param discordIds the ids to name
     * @return the handles that are known, by id; an id nobody has linked is simply absent
     */
    public Map<Long, String> handles(Collection<Long> discordIds) {
        if (discordIds.isEmpty()) return Map.of();
        var result = new HashMap<Long, String>();
        query("""
                SELECT discord_user_id, handle FROM account_discord_link
                WHERE handle IS NOT NULL AND ARRAY[discord_user_id] && ?
                """)
                .single(call().bind(List.copyOf(discordIds), PostgreSqlTypes.BIGINT))
                .map(row -> result.put(row.getLong("discord_user_id"), row.getString("handle")))
                .all();
        return result;
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

    public void setPasswordHash(int accountId, String passwordHash) {
        query("UPDATE account SET password_hash = ? WHERE id = ?")
                .single(call().bind(passwordHash).bind(accountId))
                .update();
    }

    /**
     * Records that an address has been confirmed, and makes it the account's.
     *
     * <p>One statement, because the two halves are the same fact: the address the account holds is
     * the one somebody proved they could read. Setting the address without the flag, or the flag
     * without the address, is how an account ends up marked verified for a mailbox nobody read.
     */
    public void confirmEmail(int accountId, String email) {
        query("UPDATE account SET email = ?, email_verified = TRUE WHERE id = ?")
                .single(call().bind(email).bind(accountId))
                .update();
    }

    /**
     * Stores the account's own appearance choices. A null leaves that choice to the operator's
     * default rather than pinning it, which is what "use the default" means in this table.
     */
    public void setAppearance(int accountId, String theme, String feel, String darkMode) {
        query("UPDATE account SET theme = ?, feel = ?, dark_mode = ? WHERE id = ?")
                .single(call().bind(theme).bind(feel).bind(darkMode).bind(accountId))
                .update();
    }

    public void delete(int accountId) {
        query("DELETE FROM account WHERE id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
