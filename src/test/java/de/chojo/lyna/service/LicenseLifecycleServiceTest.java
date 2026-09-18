/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.TestConf;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.LicenseGuild;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.repository.RepositoryTestBase;
import de.chojo.nexus.NexusRest;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.Member;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.sql.SQLException;
import java.sql.Statement;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A licence changing hands.
 *
 * <p>Claiming, transferring and sharing each move a row and a Discord role together. The role side
 * is answered by a guild that knows no roles, so what is asserted here is the row and the decision -
 * the role itself is the end-to-end suite's business.
 */
class LicenseLifecycleServiceTest extends RepositoryTestBase {
    private static final long GUILD = 4401L;
    private static final long OWNER = 6001L;
    private static final long OTHER = 6002L;

    private LicenseGuild licenseGuild;
    private int licenseId;

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
        Conf configuration = TestConf.defaults();
        Guilds guilds = new Guilds(Mockito.mock(NexusRest.class), configuration, accountLinks);
        licenseGuild = guilds.guild(GUILD);

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
                    VALUES (%d, 'buyer@example.invalid', 'LIFE-KEY') RETURNING id
                    """.formatted(schemaName, productId))) {
                rows.next();
                licenseId = rows.getInt(1);
            }
        }
    }

    /** A member of a guild that holds no roles, which is all these tests need of Discord. */
    private Member member(long id) {
        Guild guild = Mockito.mock(Guild.class);
        Mockito.when(guild.getIdLong()).thenReturn(GUILD);
        Mockito.when(guild.getRoleById(Mockito.anyLong())).thenReturn(null);
        // A transfer asks Discord who held it before. Nobody is there in a test, which is also what
        // happens when the previous holder has left the guild.
        @SuppressWarnings("unchecked")
        net.dv8tion.jda.api.requests.restaction.CacheRestAction<Member> absent =
                Mockito.mock(net.dv8tion.jda.api.requests.restaction.CacheRestAction.class);
        Mockito.when(absent.complete()).thenReturn(null);
        Mockito.when(guild.retrieveMemberById(Mockito.anyLong())).thenReturn(absent);
        Member member = Mockito.mock(Member.class);
        Mockito.when(member.getIdLong()).thenReturn(id);
        Mockito.when(member.getGuild()).thenReturn(guild);
        Mockito.when(member.getEffectiveName()).thenReturn("member-" + id);
        Mockito.when(member.getRoles()).thenReturn(List.of());
        return member;
    }

    private License license() {
        return licenseGuild.licenses().byId(licenseId).orElseThrow();
    }

    @Test
    @DisplayName("Claiming an unheld licence makes it theirs, and it cannot be claimed twice")
    void claiming() {
        License license = license();
        assertFalse(licenseService.isClaimed(license), "nobody holds it yet");

        assertTrue(licenseService.claim(license, member(OWNER)));
        assertEquals(OWNER, licenseService.owner(license));
        assertTrue(licenseService.isClaimed(license));

        assertFalse(licenseService.claim(license(), member(OTHER)), "it is already held");
    }

    @Test
    @DisplayName("The holder is read once and remembered")
    void ownerIsCached() {
        licenseService.claim(license(), member(OWNER));
        License license = license();

        assertEquals(OWNER, licenseService.owner(license));
        assertEquals(OWNER, licenseService.owner(license), "asking again answers the same");
    }

    @Test
    @DisplayName("Transferring moves the licence and ends every share of it")
    void transferring() {
        licenseService.claim(license(), member(OWNER));
        licenseSharing.addSharee(license(), member(OTHER));
        assertEquals(1, licenseSharing.shareCount(license()));

        assertTrue(licenseService.transfer(license(), member(OTHER)));

        assertEquals(OTHER, licenseService.owner(license()));
        assertEquals(0, licenseSharing.shareCount(license()), "the shares went with it");
    }

    @Test
    @DisplayName("A previous holder still in the guild loses the product's role")
    void transferTakesTheRoleBack() {
        licenseService.claim(license(), member(OWNER));

        Member previous = member(OWNER);
        Member arriving = member(OTHER);
        // Discord still knows the previous holder, so the transfer has somebody to take the role from.
        @SuppressWarnings("unchecked")
        net.dv8tion.jda.api.requests.restaction.CacheRestAction<Member> found =
                Mockito.mock(net.dv8tion.jda.api.requests.restaction.CacheRestAction.class);
        Mockito.when(found.complete()).thenReturn(previous);
        Mockito.when(arriving.getGuild().retrieveMemberById(Mockito.anyLong())).thenReturn(found);

        assertTrue(licenseService.transfer(license(), arriving));
        assertEquals(OTHER, licenseService.owner(license()));
    }

    @Test
    @DisplayName("Deleting ends the licence and its shares")
    void deleting() {
        licenseService.claim(license(), member(OWNER));
        licenseSharing.addSharee(license(), member(OTHER));
        License license = license();

        assertTrue(licenseService.delete(license));
        assertTrue(licenseGuild.licenses().byId(licenseId).isEmpty());
    }

    @Test
    @DisplayName("Access is granted through the service and read back")
    void access() {
        License license = license();
        assertTrue(licenseService.grantAccess(license, ReleaseType.STABLE));
        assertEquals(List.of(ReleaseType.STABLE), licenseService.access(license));
    }

    @Test
    @DisplayName("A share is added, listed and taken away again")
    void sharing() {
        License license = license();
        assertTrue(licenseSharing.addSharee(license, member(OTHER)));
        assertEquals(List.of(OTHER), licenseSharing.shareeDiscordIds(license));
        assertEquals(1, licenseSharing.sharees(license).size());

        assertTrue(licenseSharing.removeSharee(license, member(OTHER)));
        assertTrue(licenseSharing.shareeDiscordIds(license).isEmpty());
    }
}
