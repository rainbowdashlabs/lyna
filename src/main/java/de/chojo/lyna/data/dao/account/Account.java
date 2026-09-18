package de.chojo.lyna.data.dao.account;

import java.time.Instant;

/**
 * @param username      what to call this account. Taken from Discord while one is linked, chosen by
 *                      the person otherwise.
 * @param discriminator four digits that tell two people who chose the same username apart. Only a
 *                      chosen username carries one: a Discord handle is already unique on Discord's
 *                      side, so adding digits to it would invent a name nobody has.
 */
public record Account(
        int id,
        String email,
        boolean emailVerified,
        String passwordHash,
        String theme,
        String darkMode,
        String username,
        String discriminator,
        Instant createdAt,
        Instant lastLoginAt
) {
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }

    /**
     * @return the name to show, or {@code null} for an account nobody has named yet
     */
    public String displayName() {
        if (username == null || username.isBlank()) return null;
        return discriminator == null ? username : username + "#" + discriminator;
    }
}
