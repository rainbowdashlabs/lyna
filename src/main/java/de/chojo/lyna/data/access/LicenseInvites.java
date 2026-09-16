package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.account.LicenseInvite;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.sql.Timestamp;
import java.time.Duration;
import java.time.Instant;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Shares made out to an address that has no account behind it yet.
 *
 * <p>An owner buys a licence and wants to hand a place on it to somebody who has never been here.
 * Refusing until that person signs up puts the owner in charge of chasing them, so the share is
 * written against the address and waits.
 *
 * <p>It binds when that address is <em>verified</em>, never merely registered. Binding on sign-up
 * would let anybody take a share by claiming an address they do not own, which is the whole reason
 * this table stores an address rather than an account.
 */
public class LicenseInvites {
    /** How long an unanswered invite stands before it stops counting against the owner's cap. */
    public static final Duration LIFETIME = Duration.ofDays(30);

    /**
     * Invites an address onto a licence. Inviting an address that already has a standing invite
     * renews it rather than refusing, so an owner can nudge somebody who never answered.
     *
     * @return whether the invite is new rather than a renewal
     */
    public boolean invite(int licenseId, String email) {
        return query("""
                INSERT INTO license_invite (license_id, email, expires_at)
                VALUES (?, ?, now() + ?::INTERVAL)
                ON CONFLICT (license_id, email) DO UPDATE SET
                    invited_at = now(),
                    expires_at = EXCLUDED.expires_at
                RETURNING (xmax = 0) AS inserted
                """)
                .single(call().bind(licenseId).bind(email.trim()).bind("%d days".formatted(LIFETIME.toDays())))
                .map(row -> row.getBoolean("inserted"))
                .first()
                .orElse(false);
    }

    /**
     * @return whether that address had a standing invite to withdraw
     */
    public boolean withdraw(int licenseId, String email) {
        return query("DELETE FROM license_invite WHERE license_id = ? AND lower(email) = lower(?)")
                .single(call().bind(licenseId).bind(email.trim()))
                .delete()
                .changed();
    }

    /**
     * @param licenseId the licence
     * @return the invites still standing on it, oldest first
     */
    public List<LicenseInvite> standing(int licenseId) {
        return query("""
                SELECT license_id, email, invited_at, expires_at
                FROM license_invite
                WHERE license_id = ? AND expires_at > now()
                ORDER BY invited_at
                """)
                .single(call().bind(licenseId))
                .map(LicenseInvites::read)
                .all();
    }

    /**
     * Turns every standing invite for a verified address into a real share.
     *
     * <p>Called when an account proves an address is theirs, and only then. An invite that would put
     * the account on a licence it already holds is simply dropped, which is what makes calling this
     * twice harmless.
     *
     * @param accountId the account that just proved the address
     * @param email     the address it proved
     * @return the licences it was let onto
     */
    public List<Integer> bind(int accountId, String email) {
        if (email == null || email.isBlank()) return List.of();
        List<Integer> licenses = query("""
                SELECT license_id FROM license_invite
                WHERE lower(email) = lower(?) AND expires_at > now()
                """)
                .single(call().bind(email.trim()))
                .map(row -> row.getInt("license_id"))
                .all();
        for (int licenseId : licenses) {
            query("""
                    INSERT INTO user_sub_license (account_id, license_id)
                    VALUES (?, ?)
                    ON CONFLICT (account_id, license_id) DO NOTHING
                    """)
                    .single(call().bind(accountId).bind(licenseId))
                    .insert();
        }
        query("DELETE FROM license_invite WHERE lower(email) = lower(?)")
                .single(call().bind(email.trim()))
                .delete();
        return licenses;
    }

    private static LicenseInvite read(Row row) throws SQLException {
        return new LicenseInvite(
                row.getInt("license_id"),
                row.getString("email"),
                toInstant(row.getTimestamp("invited_at")),
                toInstant(row.getTimestamp("expires_at")));
    }

    private static Instant toInstant(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toInstant();
    }
}
