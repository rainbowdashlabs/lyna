package de.chojo.lyna.repository;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class RevokedJtisRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void clearRevocations() throws SQLException {
        clear("revoked_jti");
    }

    @Test
    @DisplayName("A revoked token id reads as revoked, an unknown one does not")
    void revokeAndCheck() {
        assertFalse(revokedJtis.isRevoked("jti-1"));

        revokedJtis.revoke("jti-1", Instant.now().plus(Duration.ofHours(1)));

        assertTrue(revokedJtis.isRevoked("jti-1"));
        assertFalse(revokedJtis.isRevoked("jti-2"));
    }

    @Test
    @DisplayName("Revoking the same token twice is not an error")
    void revokeIsIdempotent() {
        Instant expiresAt = Instant.now().plus(Duration.ofHours(1));

        revokedJtis.revoke("jti-repeat", expiresAt);
        revokedJtis.revoke("jti-repeat", expiresAt);

        assertTrue(revokedJtis.isRevoked("jti-repeat"));
    }

    @Test
    @DisplayName("Pruning drops the entries whose token could no longer be presented anyway")
    void pruneRemovesExpiredOnly() {
        revokedJtis.revoke("still-valid", Instant.now().plus(Duration.ofHours(1)));
        revokedJtis.revoke("long-gone", Instant.now().minus(Duration.ofHours(1)));

        assertEquals(1, revokedJtis.prune());

        assertTrue(revokedJtis.isRevoked("still-valid"));
        assertFalse(revokedJtis.isRevoked("long-gone"));
    }

    @Test
    @DisplayName("A revocation still counts while its row is there, whatever its expiry says")
    void expiredRevocationStillCountsUntilPruned() {
        revokedJtis.revoke("expired", Instant.now().minus(Duration.ofSeconds(1)));

        assertTrue(revokedJtis.isRevoked("expired"));
    }
}
