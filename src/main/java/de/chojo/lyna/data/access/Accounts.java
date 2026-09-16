package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.sadu.mapper.wrapper.Row;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.sql.SQLException;
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
        return findByIdentity(AccountIdentity.DISCORD, Long.toString(discordUserId));
    }

    /**
     * @param provider   which service the id comes from
     * @param externalId what that service calls the account
     * @return the account that identity belongs to, if it has been linked
     */
    public Optional<Account> findByIdentity(String provider, String externalId) {
        return query("""
                SELECT a.id, a.email, a.email_verified, a.password_hash, a.theme, a.feel, a.dark_mode, a.created_at, a.last_login_at
                FROM account a
                JOIN account_identity i ON i.account_id = a.id
                WHERE i.provider = ? AND i.external_id = ?
                """)
                .single(call().bind(provider).bind(externalId))
                .map(Accounts::readAccount)
                .first();
    }

    public Optional<AccountIdentity> findLinkByAccountId(int accountId) {
        return findIdentity(accountId, AccountIdentity.DISCORD);
    }

    /**
     * @return what one provider knows about an account, if it has been linked to that provider
     */
    public Optional<AccountIdentity> findIdentity(int accountId, String provider) {
        return query("""
                SELECT account_id, provider, external_id, linked_at, verified_via, handle
                FROM account_identity WHERE account_id = ? AND provider = ?
                """)
                .single(call().bind(accountId).bind(provider))
                .map(Accounts::readIdentity)
                .first();
    }

    /**
     * @return every provider this account is known to, oldest link first
     */
    public List<AccountIdentity> identities(int accountId) {
        return query("""
                SELECT account_id, provider, external_id, linked_at, verified_via, handle
                FROM account_identity WHERE account_id = ? ORDER BY linked_at
                """)
                .single(call().bind(accountId))
                .map(Accounts::readIdentity)
                .all();
    }

    public void link(int accountId, long discordUserId, AccountIdentity.Verification via) {
        link(accountId, discordUserId, via, null);
    }

    public void link(int accountId, long discordUserId, AccountIdentity.Verification via, String handle) {
        link(accountId, AccountIdentity.DISCORD, Long.toString(discordUserId), via, handle);
    }

    /**
     * Links an account to an identity at some provider, and records what that provider calls it.
     *
     * <p>A link made without a handle keeps whatever one was recorded before rather than clearing it:
     * the bot-DM path never learns a name, and losing the one an OAuth round trip found would put the
     * pages back to showing raw ids.
     *
     * <p>An identity another account already holds is refused rather than moved. Whoever controls a
     * provider account could otherwise walk it onto a second account here, and once licences hang off
     * accounts that would be a way to carry them across.
     *
     * @throws IllegalStateException if the identity belongs to a different account
     */
    public void link(int accountId, String provider, String externalId,
                     AccountIdentity.Verification via, String handle) {
        Optional<Account> holder = findByIdentity(provider, externalId);
        if (holder.isPresent() && holder.get().id() != accountId) {
            throw new IllegalStateException(
                    "That %s identity is already linked to another account".formatted(provider));
        }
        query("""
                DELETE FROM account_identity
                WHERE provider = ? AND account_id = ? AND external_id <> ?
                """)
                .single(call().bind(provider).bind(accountId).bind(externalId))
                .delete();
        query("""
                INSERT INTO account_identity (provider, external_id, account_id, verified_via, handle, handle_seen_at)
                VALUES (?, ?, ?, ?, ?, CASE WHEN ?::TEXT IS NULL THEN NULL ELSE now() END)
                ON CONFLICT (provider, external_id) DO UPDATE SET
                    linked_at      = now(),
                    verified_via   = EXCLUDED.verified_via,
                    handle         = COALESCE(EXCLUDED.handle, account_identity.handle),
                    handle_seen_at = COALESCE(EXCLUDED.handle_seen_at, account_identity.handle_seen_at)
                """)
                .single(call().bind(provider).bind(externalId).bind(accountId).bind(via.dbValue())
                        .bind(handle).bind(handle))
                .insert();
    }

    public boolean rememberHandle(long discordUserId, String handle) {
        return rememberHandle(AccountIdentity.DISCORD, Long.toString(discordUserId), handle);
    }

    /**
     * Records what a provider calls an id, wherever we happen to learn it.
     *
     * <p>Keyed by the identity rather than the account, because the bot meets people by id and does
     * not know which account, if any, they hold.
     *
     * @return whether a link for that identity was there to update
     */
    public boolean rememberHandle(String provider, String externalId, String handle) {
        if (handle == null || handle.isBlank()) return false;
        return query("""
                UPDATE account_identity SET handle = ?, handle_seen_at = now()
                WHERE provider = ? AND external_id = ? AND handle IS DISTINCT FROM ?
                """)
                .single(call().bind(handle).bind(provider).bind(externalId).bind(handle))
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
        var byId = handles(AccountIdentity.DISCORD,
                discordIds.stream().map(id -> Long.toString(id)).toList());
        var result = new HashMap<Long, String>();
        byId.forEach((externalId, handle) -> result.put(Long.parseLong(externalId), handle));
        return result;
    }

    /**
     * @param provider    which service the ids come from
     * @param externalIds the ids to name
     * @return the handles that are known, by id; an id nobody has linked is simply absent
     */
    public Map<String, String> handles(String provider, Collection<String> externalIds) {
        if (externalIds.isEmpty()) return Map.of();
        var result = new HashMap<String, String>();
        query("""
                SELECT external_id, handle FROM account_identity
                WHERE provider = ? AND handle IS NOT NULL AND ARRAY[external_id] && ?
                """)
                .single(call().bind(provider).bind(List.copyOf(externalIds), PostgreSqlTypes.TEXT))
                .map(row -> result.put(row.getString("external_id"), row.getString("handle")))
                .all();
        return result;
    }

    public void unlink(int accountId) {
        unlink(accountId, AccountIdentity.DISCORD);
    }

    public void unlink(int accountId, String provider) {
        query("DELETE FROM account_identity WHERE account_id = ? AND provider = ?")
                .single(call().bind(accountId).bind(provider))
                .delete();
    }

    /**
     * The account a Discord id belongs to, making one if it does not have any yet.
     *
     * <p>The bot hands licences to whoever is in front of it, and most of those people have never
     * opened the web at all. Licences hang off accounts, so one is minted for them: no address, no
     * password, nothing but the identity. Signing in through Discord later lands on that same
     * account and finds the licences already there, because it is reached by the same identity.
     *
     * @param discordUserId the Discord id
     * @return the account id, never zero
     */
    public static int accountIdForDiscord(long discordUserId) {
        String externalId = Long.toString(discordUserId);
        Optional<Integer> existing = query("""
                SELECT account_id FROM account_identity WHERE provider = ? AND external_id = ?
                """)
                .single(call().bind(AccountIdentity.DISCORD).bind(externalId))
                .map(row -> row.getInt("account_id"))
                .first();
        if (existing.isPresent()) return existing.get();

        int accountId = query("INSERT INTO account (email, password_hash) VALUES (NULL, NULL) RETURNING id")
                .single(call())
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow(() -> new IllegalStateException("Could not create an account for " + externalId));
        Optional<Integer> linked = query("""
                INSERT INTO account_identity (provider, external_id, account_id, verified_via)
                VALUES (?, ?, ?, ?)
                ON CONFLICT (provider, external_id) DO UPDATE SET external_id = EXCLUDED.external_id
                RETURNING account_id
                """)
                .single(call().bind(AccountIdentity.DISCORD).bind(externalId).bind(accountId)
                        .bind(AccountIdentity.Verification.BOT_DM_CODE.dbValue()))
                .map(row -> row.getInt("account_id"))
                .first();
        int resolved = linked.orElseThrow(
                () -> new IllegalStateException("Could not link an account for " + externalId));
        if (resolved != accountId) {
            delete(accountId);
        }
        return resolved;
    }

    private static AccountIdentity readIdentity(Row row) throws SQLException {
        return new AccountIdentity(
                row.getInt("account_id"),
                row.getString("provider"),
                row.getString("external_id"),
                toInstant(row.getTimestamp("linked_at")),
                row.getString("verified_via"),
                row.getString("handle"));
    }

    private static Account readAccount(Row row) throws SQLException {
        return new Account(
                row.getInt("id"),
                row.getString("email"),
                row.getBoolean("email_verified"),
                row.getString("password_hash"),
                row.getString("theme"),
                row.getString("feel"),
                row.getString("dark_mode"),
                toInstant(row.getTimestamp("created_at")),
                toInstant(row.getTimestamp("last_login_at")));
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

    public static void delete(int accountId) {
        query("DELETE FROM account WHERE id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
