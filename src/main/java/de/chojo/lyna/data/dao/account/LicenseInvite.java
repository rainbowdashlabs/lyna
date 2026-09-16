package de.chojo.lyna.data.dao.account;

import java.time.Instant;

/**
 * A share made out to an address that nobody has claimed yet.
 *
 * @param email the address invited. Shown back to the owner who typed it and to nobody else - a
 *              sharee list names people by username precisely so that one person's address is never
 *              handed to another.
 */
public record LicenseInvite(
        int licenseId,
        String email,
        Instant invitedAt,
        Instant expiresAt
) {
}
