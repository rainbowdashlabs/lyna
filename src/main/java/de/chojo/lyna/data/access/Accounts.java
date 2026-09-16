package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import de.chojo.lyna.data.dao.licenses.LicenseSource;
import de.chojo.sadu.mapper.wrapper.Row;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.ThreadLocalRandom;
import java.util.regex.Pattern;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class Accounts {
    private final LicenseInvites invites = new LicenseInvites();
    private final AccountEmails emails = new AccountEmails();


    /**
     * Creates an account, claiming an address for it if one was given.
     *
     * <p>The address arrives unverified: creating an account is not proof that somebody reads the
     * inbox they typed.
     *
     * @throws IllegalStateException if the address already belongs to somebody
     */
    public Account create(String email, String passwordHash) {
        int id = query("INSERT INTO account (password_hash) VALUES (?) RETURNING id")
                .single(call().bind(passwordHash))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow(() -> new IllegalStateException("Failed to insert account"));
        if (email != null && !email.isBlank()) {
            try {
                emails.add(id, email);
            } catch (RuntimeException e) {
                delete(id);
                throw e;
            }
        }
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
        if (AccountIdentity.DISCORD.equals(provider)) {
            syncUsernameFromHandle(accountId, handle);
        }
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
        if (changed && AccountIdentity.DISCORD.equals(provider)) {
            findByIdentity(provider, externalId)
                    .ifPresent(account -> syncUsernameFromHandle(account.id(), handle));
        }
        return changed;
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
        if (AccountIdentity.DISCORD.equals(provider)) {
            detachUsername(accountId);
        }
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

        int accountId = query("INSERT INTO account (password_hash) VALUES (NULL) RETURNING id")
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

    /**
     * What somebody may call themselves, before the digits are added.
     *
     * <p>Letters, digits, and the three separators people expect in a handle. No spaces and no
     * punctuation that could make one name read as another.
     */
    private static final Pattern USERNAME = Pattern.compile("^[A-Za-z0-9](?:[A-Za-z0-9._-]{1,30})[A-Za-z0-9]$");

    private static final int DISCRIMINATOR_ATTEMPTS = 12;

    /**
     * Gives an account a name of its own choosing.
     *
     * <p>Refused while a Discord identity is linked: that name is Discord's to change, and letting
     * both sides write it would mean the next sign-in quietly undid whatever was typed here.
     *
     * <p>The four digits are allocated here rather than asked for, so two people may both be "ada"
     * without either of them finding out what the other picked.
     *
     * @param accountId the account to name
     * @param username  the name, without digits
     * @return the discriminator it was given
     * @throws IllegalArgumentException if the name is not one somebody may take
     * @throws IllegalStateException    if the account is linked, or the name has no free digits left
     */
    public String setUsername(int accountId, String username) {
        String trimmed = username == null ? "" : username.trim();
        if (!USERNAME.matcher(trimmed).matches()) {
            throw new IllegalArgumentException(
                    "A username is 3 to 32 characters of letters, digits, dots, dashes or underscores");
        }
        if (findLinkByAccountId(accountId).isPresent()) {
            throw new IllegalStateException("This account is named by Discord. Unlink it to choose a name.");
        }
        for (int attempt = 0; attempt < DISCRIMINATOR_ATTEMPTS; attempt++) {
            String discriminator = "%04d".formatted(ThreadLocalRandom.current().nextInt(1, 10_000));
            if (writeUsername(accountId, trimmed, discriminator)) return discriminator;
        }
        for (String discriminator : freeDiscriminators(trimmed)) {
            if (writeUsername(accountId, trimmed, discriminator)) return discriminator;
        }
        throw new IllegalStateException("Every discriminator for that username is taken. Pick another.");
    }

    /**
     * @return false when those four digits are already taken for that name
     */
    private boolean writeUsername(int accountId, String username, String discriminator) {
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
     * @return the digits nobody holds for that name, so a name that is nearly full still resolves
     */
    private List<String> freeDiscriminators(String username) {
        Set<String> taken = Set.copyOf(query("""
                SELECT discriminator FROM account
                WHERE lower(username) = lower(?) AND discriminator IS NOT NULL
                """)
                .single(call().bind(username))
                .map(row -> row.getString("discriminator"))
                .all());
        List<String> free = new ArrayList<>();
        for (int candidate = 1; candidate < 10_000; candidate++) {
            String discriminator = "%04d".formatted(candidate);
            if (!taken.contains(discriminator)) free.add(discriminator);
        }
        return free;
    }

    /**
     * Takes the account's name from the provider that owns it.
     *
     * <p>The digits go: a handle is unique where it comes from, and keeping stale digits beside it
     * would show a name that exists nowhere.
     */
    public void syncUsernameFromHandle(int accountId, String handle) {
        if (handle == null || handle.isBlank()) return;
        query("UPDATE account SET username = ?, discriminator = NULL WHERE id = ?")
                .single(call().bind(handle.trim()).bind(accountId))
                .update();
    }

    /**
     * Gives an account's name digits of its own, for when the provider that guaranteed it was unique
     * is no longer linked.
     *
     * <p>The name itself is left alone. Somebody who has been "ada" to their sharees stays "ada",
     * and only gains the digits that keep them apart from the next one.
     */
    public void detachUsername(int accountId) {
        Optional<Account> account = findById(accountId);
        if (account.isEmpty()) return;
        String username = account.get().username();
        if (username == null || username.isBlank() || account.get().discriminator() != null) return;
        for (int attempt = 0; attempt < DISCRIMINATOR_ATTEMPTS; attempt++) {
            String discriminator = "%04d".formatted(ThreadLocalRandom.current().nextInt(1, 10_000));
            if (writeUsername(accountId, username, discriminator)) return;
        }
        for (String discriminator : freeDiscriminators(username)) {
            if (writeUsername(accountId, username, discriminator)) return;
        }
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
                .map(Accounts::readAccount)
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
     * Records that an account has proved an address is theirs.
     *
     * <p>Any licence invited to that address is bound here rather than at the call site, so that
     * every way of verifying an address lets somebody onto the licences waiting for them.
     *
     * @return the licences the account was let onto by invites standing for that address
     */
    public List<Integer> confirmEmail(int accountId, String email) {
        emails.add(accountId, email);
        emails.verify(accountId, email);
        if (emails.primary(accountId).isEmpty()) {
            emails.makePrimary(accountId, email);
        }
        return collect(accountId, email);
    }

    /**
     * Hands the account what was waiting on that address.
     *
     * <p>Two things arrive this way: a licence somebody invited the address onto, and a licence bought
     * with it in the shop. The second is the point of an account holding more than one address at all
     * - somebody who paid from one address and signed up with another otherwise has to carry the key
     * across by hand.
     *
     * <p>Only a licence nobody holds. One that has already been claimed stays with whoever claimed it:
     * proving an address is a way to find a purchase, not a way to take one.
     *
     * @return the licences the account now holds because of this address
     */
    private List<Integer> collect(int accountId, String email) {
        List<Integer> collected = new java.util.ArrayList<>(invites.bind(accountId, email));
        List<Integer> bought = query("""
                SELECT l.id
                FROM license l
                WHERE l.source = ?
                  AND LOWER(l.user_identifier) = LOWER(?)
                  AND NOT EXISTS (SELECT 1 FROM user_license u WHERE u.license_id = l.id)
                """)
                .single(call().bind(LicenseSource.KOFI.name()).bind(email.trim()))
                .map(row -> row.getInt("id"))
                .all();
        for (int licenseId : bought) {
            query("""
                    INSERT INTO user_license (account_id, license_id) VALUES (?, ?)
                    ON CONFLICT (license_id) DO NOTHING
                    """)
                    .single(call().bind(accountId).bind(licenseId))
                    .insert();
            collected.add(licenseId);
        }
        return collected;
    }

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
        query("DELETE FROM account WHERE id = ?")
                .single(call().bind(accountId))
                .delete();
    }

    private static Instant toInstant(Timestamp ts) {
        return ts == null ? null : ts.toInstant();
    }
}
