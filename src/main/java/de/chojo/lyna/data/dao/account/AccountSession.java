package de.chojo.lyna.data.dao.account;

import java.time.Instant;

public record AccountSession(
        String jti,
        int accountId,
        Instant issuedAt,
        Instant expiresAt,
        Instant lastSeenAt,
        String userAgent
) {
}
