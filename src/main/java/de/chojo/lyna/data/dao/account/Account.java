package de.chojo.lyna.data.dao.account;

import java.time.Instant;

public record Account(
        int id,
        String email,
        boolean emailVerified,
        String passwordHash,
        String theme,
        String feel,
        String darkMode,
        Instant createdAt,
        Instant lastLoginAt
) {
    public boolean hasPassword() {
        return passwordHash != null && !passwordHash.isBlank();
    }
}
