/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.AccountEmail;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The addresses an account holds.
 *
 * <p>Several per account, because people use different addresses in different places - the one on
 * their Discord account, the one they paid from, the one on a second shop account. One account per
 * address, because an address is how a licence bought elsewhere finds its buyer, and that has to name
 * exactly one person.
 *
 * <p>Only a verified address counts for anything. Adding one is a claim; following the link sent to
 * it is what makes it true.
 */
public class AccountEmails {

    /**
     * @return every address the account holds, the primary first and the rest oldest first
     */
    public List<AccountEmail> of(int accountId) {
        return query("""
                SELECT account_id, email, verified_at, added_at, is_primary
                FROM account_email
                WHERE account_id = ?
                ORDER BY is_primary DESC, added_at
                """)
                .single(call().bind(accountId))
                .map(AccountEmails::read)
                .all();
    }

    /**
     * @return the address the application sends to, if the account has one
     */
    public Optional<AccountEmail> primary(int accountId) {
        return query("""
                SELECT account_id, email, verified_at, added_at, is_primary
                FROM account_email
                WHERE account_id = ? AND is_primary
                """)
                .single(call().bind(accountId))
                .map(AccountEmails::read)
                .first();
    }

    /**
     * @param email the address, however it was capitalised
     * @return the account holding it, verified or not
     */
    /**
     * The account somebody would be found by that address, if any.
     *
     * <p>Only a proved address or the one an account is written to: those are exclusive, so there is
     * at most one. A merely claimed address is not exclusive, and is nobody's until it is proved.
     */
    public Optional<AccountEmail> byAddress(String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return query("""
                SELECT account_id, email, verified_at, added_at, is_primary
                FROM account_email
                WHERE LOWER(email) = LOWER(?) AND (verified_at IS NOT NULL OR is_primary)
                """)
                .single(call().bind(email.trim()))
                .map(AccountEmails::read)
                .first();
    }

    /**
     * @return the row for this account's own claim on the address, proved or not
     */
    public Optional<AccountEmail> heldBy(int accountId, String email) {
        if (email == null || email.isBlank()) return Optional.empty();
        return query("""
                SELECT account_id, email, verified_at, added_at, is_primary
                FROM account_email
                WHERE account_id = ? AND LOWER(email) = LOWER(?)
                """)
                .single(call().bind(accountId).bind(email.trim()))
                .map(AccountEmails::read)
                .first();
    }

    /**
     * Claims an address for an account.
     *
     * <p>The first address an account holds becomes the one it is written to. Nothing is proved here:
     * the address arrives unverified and stays that way until a link sent to it is followed.
     *
     * @throws IllegalStateException if another account already holds it
     */
    public void add(int accountId, String email) {
        String trimmed = email.trim();
        if (heldBy(accountId, trimmed).isPresent()) return;
        Optional<AccountEmail> owner = byAddress(trimmed);
        if (owner.isPresent() && owner.get().accountId() != accountId) {
            throw new IllegalStateException("That address belongs to another account");
        }
        boolean first = of(accountId).isEmpty();
        query("""
                INSERT INTO account_email (account_id, email, is_primary)
                VALUES (?, ?, ?)
                """).single(call().bind(accountId).bind(trimmed).bind(first)).insert();
    }

    /**
     * Records that a link sent to the address was followed.
     *
     * @return whether the address was there to verify and belongs to that account
     */
    /**
     * @throws IllegalStateException if another account proved it first, which is how a race between
     *                               two claims on the same address is settled
     */
    public boolean verify(int accountId, String email) {
        if (email == null || email.isBlank()) return false;
        Optional<AccountEmail> owner = byAddress(email);
        if (owner.isPresent()
                && owner.get().accountId() != accountId
                && owner.get().verified()) {
            throw new IllegalStateException("That address belongs to another account");
        }
        return query("""
                UPDATE account_email SET verified_at = COALESCE(verified_at, now())
                WHERE account_id = ? AND LOWER(email) = LOWER(?)
                """)
                .single(call().bind(accountId).bind(email.trim()))
                .update()
                .changed();
    }

    /**
     * Makes one of the account's addresses the one it is written to.
     *
     * <p>Only a verified one: the primary address is where a password reset is sent, and sending that
     * to an address nobody has proved would be handing the account to whoever holds it.
     *
     * @return whether the address was the account's and verified
     */
    public boolean makePrimary(int accountId, String email) {
        Optional<AccountEmail> candidate = heldBy(accountId, email);
        if (candidate.isEmpty() || !candidate.get().verified()) {
            return false;
        }
        query("UPDATE account_email SET is_primary = FALSE WHERE account_id = ? AND is_primary")
                .single(call().bind(accountId))
                .update();
        query("""
                UPDATE account_email SET is_primary = TRUE
                WHERE account_id = ? AND LOWER(email) = LOWER(?)
                """).single(call().bind(accountId).bind(email.trim())).update();
        return true;
    }

    /**
     * Gives up an address.
     *
     * <p>The primary is refused: something has to be the address the account is written to, and
     * choosing the next one is the account holder's decision rather than this method's.
     *
     * @return whether it was removed
     */
    public boolean remove(int accountId, String email) {
        Optional<AccountEmail> candidate = heldBy(accountId, email);
        if (candidate.isEmpty() || candidate.get().primary()) {
            return false;
        }
        return query("DELETE FROM account_email WHERE account_id = ? AND LOWER(email) = LOWER(?)")
                .single(call().bind(accountId).bind(email.trim()))
                .delete()
                .changed();
    }

    private static AccountEmail read(Row row) throws SQLException {
        return new AccountEmail(
                row.getInt("account_id"),
                row.getString("email"),
                toInstant(row.getTimestamp("verified_at")),
                toInstant(row.getTimestamp("added_at")),
                row.getBoolean("is_primary"));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
