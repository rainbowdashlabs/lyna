/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.license.entity.Sharee;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.sql.Statement;
import java.time.Instant;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Who holds a licence and who it is shared with, as rows.
 */
class LicenseRepositoryTest extends RepositoryTestBase {
    private static final long GUILD = 4301L;
    private static final long OWNER = 5001L;
    private static final long SHAREE = 5002L;

    private int licenseId;
    private int ownerAccount;
    private int shareeAccount;

    @BeforeEach
    void seed() throws SQLException {
        clear(
                "license_invite",
                "user_sub_license",
                "user_license",
                "license_access",
                "license",
                "product",
                "account_email",
                "account_identity",
                "account");
        ownerAccount = accountLinks.accountIdForDiscord(OWNER);
        shareeAccount = accountLinks.accountIdForDiscord(SHAREE);
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            int productId;
            try (var rows = statement.executeQuery(
                    "INSERT INTO %s.product (guild_id, name, role) VALUES (%d, 'Widget', 5) RETURNING id"
                            .formatted(schemaName, GUILD))) {
                rows.next();
                productId = rows.getInt(1);
            }
            try (var rows = statement.executeQuery("""
                    INSERT INTO %s.license (product_id, user_identifier, key)
                    VALUES (%d, 'buyer@example.invalid', 'LIC-KEY') RETURNING id
                    """.formatted(schemaName, productId))) {
                rows.next();
                licenseId = rows.getInt(1);
            }
        }
    }

    @Test
    @DisplayName("Claiming names a holder, and a second claim does not move it")
    void claimIsOnce() {
        assertTrue(licenseRepository.claim(ownerAccount, licenseId));
        assertEquals(OWNER, licenseRepository.ownerDiscordId(licenseId).orElseThrow());

        assertFalse(licenseRepository.claim(shareeAccount, licenseId), "somebody already holds it");
        assertEquals(OWNER, licenseRepository.ownerDiscordId(licenseId).orElseThrow());
    }

    @Test
    @DisplayName("Transferring moves it whoever held it")
    void transferMoves() {
        licenseRepository.claim(ownerAccount, licenseId);
        assertTrue(licenseRepository.transfer(shareeAccount, licenseId));
        assertEquals(SHAREE, licenseRepository.ownerDiscordId(licenseId).orElseThrow());
    }

    @Test
    @DisplayName("A licence nobody holds names nobody")
    void unheldNamesNobody() {
        assertTrue(licenseRepository.ownerDiscordId(licenseId).isEmpty());
    }

    @Test
    @DisplayName("Sharees are listed by Discord id and by name, and cleared together")
    void shareesAreListedAndCleared() {
        assertTrue(licenseRepository.addSharee(licenseId, shareeAccount));
        assertFalse(licenseRepository.addSharee(licenseId, shareeAccount), "twice shares nothing new");

        assertEquals(List.of(SHAREE), licenseRepository.shareeDiscordIds(licenseId));
        List<Sharee> named = licenseRepository.sharees(licenseId);
        assertEquals(1, named.size());
        assertEquals(SHAREE, named.get(0).discordId());

        licenseRepository.clearSharees(licenseId);
        assertTrue(licenseRepository.shareeDiscordIds(licenseId).isEmpty());
    }

    @Test
    @DisplayName("A sharee with no Discord identity is still counted and still named")
    void webOnlyShareesAreNamed() {
        var webOnly = accountService.register("web@example.invalid", "hash");
        usernameService.setUsername(webOnly.id(), "ada");
        licenseRepository.addSharee(licenseId, webOnly.id());

        assertTrue(licenseRepository.shareeDiscordIds(licenseId).isEmpty(), "Discord knows nothing of them");
        List<Sharee> named = licenseRepository.sharees(licenseId);
        assertEquals(1, named.size());
        assertEquals(null, named.get(0).discordId());
        assertTrue(named.get(0).name().startsWith("ada#"), "named by their own name and digits");
    }

    @Test
    @DisplayName("Removing one share leaves the others")
    void removeIsPerSharee() {
        licenseRepository.addSharee(licenseId, shareeAccount);
        licenseRepository.addSharee(licenseId, ownerAccount);

        assertTrue(licenseRepository.removeSharee(licenseId, shareeAccount));
        assertEquals(List.of(OWNER), licenseRepository.shareeDiscordIds(licenseId));
        assertFalse(licenseRepository.removeSharee(licenseId, shareeAccount), "already gone");
    }

    @Test
    @DisplayName("The share count holds a place for an invite nobody has answered")
    void invitesHoldAPlace() {
        assertEquals(0, licenseRepository.shareCount(licenseId));

        licenseRepository.addSharee(licenseId, shareeAccount);
        assertEquals(1, licenseRepository.shareCount(licenseId));

        licenseInvites.invite(licenseId, "invited@example.invalid");
        assertEquals(2, licenseRepository.shareCount(licenseId), "an unanswered invite still holds a place");
    }

    @Test
    @DisplayName("An expired invite holds no place")
    void expiredInvitesDoNotCount() throws SQLException {
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement()) {
            statement.execute("""
                    INSERT INTO %s.license_invite (license_id, email, expires_at)
                    VALUES (%d, 'stale@example.invalid', '%s')
                    """.formatted(
                    schemaName, licenseId, java.sql.Timestamp.from(Instant.now().minusSeconds(60))));
        }
        assertEquals(0, licenseRepository.shareCount(licenseId));
    }

    @Test
    @DisplayName("Access is granted once per release type, and read back")
    void accessIsGranted() {
        assertTrue(licenseRepository.grantAccess(licenseId, ReleaseType.STABLE));
        assertFalse(licenseRepository.grantAccess(licenseId, ReleaseType.STABLE), "twice grants nothing");
        assertTrue(licenseRepository.grantAccess(licenseId, ReleaseType.DEV));

        assertEquals(2, licenseRepository.access(licenseId).size());
        assertTrue(licenseRepository.access(licenseId).contains(ReleaseType.STABLE));
    }

    @Test
    @DisplayName("Deleting takes the licence and what hung from it")
    void deleteRemovesIt() {
        licenseRepository.claim(ownerAccount, licenseId);
        licenseRepository.grantAccess(licenseId, ReleaseType.STABLE);

        assertTrue(licenseRepository.delete(licenseId));
        assertTrue(licenseRepository.ownerDiscordId(licenseId).isEmpty());
        assertTrue(licenseRepository.access(licenseId).isEmpty());
        assertFalse(licenseRepository.delete(licenseId));
    }
}
