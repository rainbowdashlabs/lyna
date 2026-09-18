/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.repository;

import de.chojo.lyna.feature.account.entity.Account;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
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
import java.util.Set;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class AccountRepository {

    /**
     * Writes an account with no address.
     *
     * @return the new account
     */
    public Account insert(String passwordHash) {
        int id = query("INSERT INTO account (password_hash) VALUES (?) RETURNING id")
                .single(call().bind(passwordHash))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow(() -> new IllegalStateException("Failed to insert account"));
        return findById(id).orElseThrow(() -> new IllegalStateException("Failed to read the new account"));
    }

    /**
     * Writes an account and the address it is written to, in one statement.
     *
     * <p>One statement rather than two, because each takes a connection of its own: a signup that
     * spent four of them put a small pool under enough pressure to start failing requests.
     *
     * <p>The address arrives unverified. Creating an account is not proof that somebody reads the
     * inbox they typed.
     *
     * @return the new account
     */
    public Account insertWithPrimaryEmail(String passwordHash, String email) {
        int id = query("""
                WITH created AS (
                    INSERT INTO account (password_hash) VALUES (?) RETURNING id
                )
                INSERT INTO account_email (account_id, email, is_primary)
                SELECT id, ?, TRUE FROM created
                RETURNING account_id
                """)
                .single(call().bind(passwordHash).bind(email.trim()))
                .map(row -> row.getInt("account_id"))
                .first()
                .orElseThrow(() -> new IllegalStateException("Failed to insert account"));
        return findById(id).orElseThrow(() -> new IllegalStateException("Failed to read the new account"));
    }

    public Optional<Account> findById(int id) {
        return query("""
                SELECT a.id, e.email, (e.verified_at IS NOT NULL) AS email_verified, a.password_hash, a.theme,
                       a.dark_mode, a.username, a.discriminator, a.created_at, a.last_login_at
                FROM account a LEFT JOIN account_email e ON e.account_id = a.id AND e.is_primary
                WHERE a.id = ?
                """)
                .single(call().bind(id))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getBoolean("email_verified"),
                        row.getString("password_hash"),
                        row.getString("theme"),
                        row.getString("dark_mode"),
                        row.getString("username"),
                        row.getString("discriminator"),
                        toInstant(row.getTimestamp("created_at")),
                        toInstant(row.getTimestamp("last_login_at"))))
                .first();
    }

    /**
     * The account somebody signs in as with that address.
     *
     * <p>Any of the account's proved addresses, or the one it is written to - the two that are
     * exclusive. A merely claimed address is not: two people may both have typed it, so it names
     * nobody until one of them proves it.
     */
    public Optional<Account> findByEmail(String email) {
        return query("""
                SELECT a.id, e.email, (e.verified_at IS NOT NULL) AS email_verified, a.password_hash, a.theme,
                       a.dark_mode, a.username, a.discriminator, a.created_at, a.last_login_at
                FROM account a LEFT JOIN account_email e ON e.account_id = a.id AND e.is_primary
                WHERE EXISTS (SELECT 1 FROM account_email m
                              WHERE m.account_id = a.id
                                AND LOWER(m.email) = LOWER(?)
                                AND (m.verified_at IS NOT NULL OR m.is_primary))
                """)
                .single(call().bind(email))
                .map(row -> new Account(
                        row.getInt("id"),
                        row.getString("email"),
                        row.getBoolean("email_verified"),
                        row.getString("password_hash"),
                        row.getString("theme"),
                        row.getString("dark_mode"),
                        row.getString("username"),
                        row.getString("discriminator"),
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
                SELECT a.id, e.email, (e.verified_at IS NOT NULL) AS email_verified, a.password_hash, a.theme,
                       a.dark_mode, a.username, a.discriminator, a.created_at, a.last_login_at
                FROM account a LEFT JOIN account_email e ON e.account_id = a.id AND e.is_primary
                JOIN account_identity i ON i.account_id = a.id
                WHERE i.provider = ? AND i.external_id = ?
                """)
                .single(call().bind(provider).bind(externalId))
                .map(AccountRepository::readAccount)
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
                .map(AccountRepository::readIdentity)
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
                .map(AccountRepository::readIdentity)
                .all();
    }

    /**
     * Drops any other identity this account held at the same provider.
     *
     * <p>One identity per provider per account: the column pair is unique, so an account moving from
     * one identity to another has to let go of the first.
     */
    public void dropOtherIdentities(int accountId, String provider, String externalId) {
        query("""
                DELETE FROM account_identity
                WHERE provider = ? AND account_id = ? AND external_id <> ?
                """)
                .single(call().bind(provider).bind(accountId).bind(externalId))
                .delete();
    }

    /**
     * Writes an identity, keeping whatever handle was recorded before when none is given.
     *
     * <p>The bot-DM path never learns a name, and clearing the one an OAuth round trip found would
     * put the pages back to showing raw ids.
     */
    public void upsertIdentity(
            int accountId, String provider, String externalId, AccountIdentity.Verification via, String handle) {
        query("""
                INSERT INTO account_identity (provider, external_id, account_id, verified_via, handle, handle_seen_at)
                VALUES (?, ?, ?, ?, ?, CASE WHEN ?::TEXT IS NULL THEN NULL ELSE now() END)
                ON CONFLICT (provider, external_id) DO UPDATE SET
                    linked_at      = now(),
                    verified_via   = EXCLUDED.verified_via,
                    handle         = COALESCE(EXCLUDED.handle, account_identity.handle),
                    handle_seen_at = COALESCE(EXCLUDED.handle_seen_at, account_identity.handle_seen_at)
                """)
                .single(call().bind(provider)
                        .bind(externalId)
                        .bind(accountId)
                        .bind(via.dbValue())
                        .bind(handle)
                        .bind(handle))
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
        boolean changed = query("""
                UPDATE account_identity SET handle = ?, handle_seen_at = now()
                WHERE provider = ? AND external_id = ? AND handle IS DISTINCT FROM ?
                """)
                .single(call().bind(handle).bind(provider).bind(externalId).bind(handle))
                .update()
                .changed();
        return changed;
    }

    /**
     * What a set of Discord ids are called, for a page naming several people at once.
     *
     * @param discordIds the ids to name
     * @return the handles that are known, by id; an id nobody has linked is simply absent
     */
    public Map<Long, String> handles(Collection<Long> discordIds) {
        var byId = handles(
                AccountIdentity.DISCORD,
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

    /**
     * Forgets an account's identity at one provider.
     */
    public void deleteIdentity(int accountId, String provider) {
        query("DELETE FROM account_identity WHERE account_id = ? AND provider = ?")
                .single(call().bind(accountId).bind(provider))
                .delete();
    }

    /**
     * Forgets every identity an account holds.
     */
    public void deleteIdentities(int accountId) {
        query("DELETE FROM account_identity WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    /**
     * @return the account an identity belongs to, without reading the account itself
     */
    public Optional<Integer> accountIdForIdentity(String provider, String externalId) {
        return query("SELECT account_id FROM account_identity WHERE provider = ? AND external_id = ?")
                .single(call().bind(provider).bind(externalId))
                .map(row -> row.getInt("account_id"))
                .first();
    }

    /**
     * Writes a name and its digits.
     *
     * <p>The clash is caught here because it is the database that decides it: the unique index over
     * name and digits is what makes two people called "ada" distinguishable.
     *
     * @return false when those four digits are already taken for that name
     */
    public boolean writeUsername(int accountId, String username, String discriminator) {
        try {
            query("UPDATE account SET username = ?, discriminator = ? WHERE id = ?")
                    .single(call().bind(username).bind(discriminator).bind(accountId))
                    .update();
            return true;
        } catch (Exception e) {
            if (isUsernameClash(e)) return false;
            throw e;
        }
    }

    private static boolean isUsernameClash(Throwable e) {
        for (Throwable cause = e; cause != null; cause = cause.getCause()) {
            String message = cause.getMessage();
            if (message != null && message.contains("account_username_unique")) return true;
        }
        return false;
    }

    /**
     * Writes a name that a provider guarantees is unique, so it carries no digits.
     */
    public void writeProvidedUsername(int accountId, String handle) {
        query("UPDATE account SET username = ?, discriminator = NULL WHERE id = ?")
                .single(call().bind(handle).bind(accountId))
                .update();
    }

    /**
     * @return the digits already taken for that name
     */
    public Set<String> takenDiscriminators(String username) {
        return Set.copyOf(query("""
                SELECT discriminator FROM account
                WHERE lower(username) = lower(?) AND discriminator IS NOT NULL
                """)
                .single(call().bind(username))
                .map(row -> row.getString("discriminator"))
                .all());
    }

    /**
     * @param displayName a name as it is shown, with or without its digits
     * @return the account of that name, if somebody holds it
     */
    public Optional<Account> findByUsername(String displayName) {
        String trimmed = displayName == null ? "" : displayName.trim();
        int hash = trimmed.lastIndexOf('#');
        String username = hash < 0 ? trimmed : trimmed.substring(0, hash);
        String discriminator = hash < 0 ? null : trimmed.substring(hash + 1);
        if (username.isBlank()) return Optional.empty();
        return query("""
                SELECT a.id, e.email, (e.verified_at IS NOT NULL) AS email_verified, a.password_hash, a.theme,
                       a.dark_mode, a.username, a.discriminator, a.created_at, a.last_login_at
                FROM account a LEFT JOIN account_email e ON e.account_id = a.id AND e.is_primary
                WHERE lower(a.username) = lower(?) AND a.discriminator IS NOT DISTINCT FROM ?
                """)
                .single(call().bind(username).bind(discriminator))
                .map(AccountRepository::readAccount)
                .first();
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
                row.getString("dark_mode"),
                row.getString("username"),
                row.getString("discriminator"),
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
    /**
     * Stores the account's own appearance choices. A null leaves that choice to the operator's
     * default rather than pinning it, which is what "use the default" means in this table.
     */
    public void setAppearance(int accountId, String theme, String darkMode) {
        query("UPDATE account SET theme = ?, dark_mode = ? WHERE id = ?")
                .single(call().bind(theme).bind(darkMode).bind(accountId))
                .update();
    }

    public static void delete(int accountId) {
        query("DELETE FROM account WHERE id = ?").single(call().bind(accountId)).delete();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
