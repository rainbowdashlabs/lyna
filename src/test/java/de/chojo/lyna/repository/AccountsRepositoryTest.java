/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.AccountIdentity;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountsRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void clearAccounts() throws SQLException {
        clear("account_identity", "account");
    }

    @Test
    @DisplayName("An account is found again by its id and by its email")
    void createAndFind() {
        Account created = accounts.create("someone@example.invalid", "hash");

        assertTrue(created.id() > 0);
        assertEquals("someone@example.invalid", created.email());
        assertNotNull(created.createdAt());

        assertEquals(created.id(), accounts.findById(created.id()).orElseThrow().id());
        assertEquals(
                created.id(),
                accounts.findByEmail("someone@example.invalid").orElseThrow().id());
    }

    @Test
    @DisplayName("Email lookup ignores the case the address was typed in")
    void findByEmailIsCaseInsensitive() {
        Account created = accounts.create("Mixed.Case@example.invalid", "hash");

        assertEquals(
                created.id(),
                accounts.findByEmail("mixed.case@example.invalid").orElseThrow().id());
        assertEquals(
                created.id(),
                accounts.findByEmail("MIXED.CASE@EXAMPLE.INVALID").orElseThrow().id());
    }

    @Test
    @DisplayName("Nothing is found for an id or an address that was never created")
    void findMissing() {
        assertTrue(accounts.findById(424242).isEmpty());
        assertTrue(accounts.findByEmail("nobody@example.invalid").isEmpty());
        assertTrue(accounts.findByDiscordId(1L).isEmpty());
    }

    @Test
    @DisplayName("The same address cannot be registered twice")
    void emailIsUnique() {
        accounts.create("duplicate@example.invalid", "hash");

        assertThrows(RuntimeException.class, () -> accounts.create("duplicate@example.invalid", "other"));
    }

    @Test
    @DisplayName("An account signed up through Discord carries no password")
    void createWithoutPassword() {
        Account created = accounts.create(null, null);

        Account found = accounts.findById(created.id()).orElseThrow();
        assertNull(found.email());
        assertFalse(found.hasPassword());
    }

    @Test
    @DisplayName("A linked Discord id finds the account, and unlinking takes it away again")
    void linkAndUnlink() {
        Account created = accounts.create("linked@example.invalid", "hash");

        accounts.link(created.id(), 1234567890L, AccountIdentity.Verification.OAUTH);

        assertEquals(
                created.id(),
                accounts.findByDiscordId(1234567890L).orElseThrow().id());
        AccountIdentity link = accounts.findLinkByAccountId(created.id()).orElseThrow();
        assertEquals(1234567890L, link.externalIdAsLong());
        assertEquals("oauth", link.verifiedVia());
        assertNotNull(link.linkedAt());

        accounts.unlink(created.id());

        assertTrue(accounts.findByDiscordId(1234567890L).isEmpty());
        assertTrue(accounts.findLinkByAccountId(created.id()).isEmpty());
    }

    @Test
    @DisplayName("Linking again replaces the Discord id rather than adding a second one")
    void relinkReplaces() {
        Account created = accounts.create("relink@example.invalid", "hash");

        accounts.link(created.id(), 111L, AccountIdentity.Verification.OAUTH);
        accounts.link(created.id(), 222L, AccountIdentity.Verification.BOT_DM_CODE);

        assertTrue(accounts.findByDiscordId(111L).isEmpty());
        assertEquals(created.id(), accounts.findByDiscordId(222L).orElseThrow().id());
        assertEquals(
                "bot_dm_code",
                accounts.findLinkByAccountId(created.id()).orElseThrow().verifiedVia());
    }

    @Test
    @DisplayName("A Discord id belongs to one account at a time")
    void discordIdIsUnique() {
        Account first = accounts.create("first@example.invalid", "hash");
        Account second = accounts.create("second@example.invalid", "hash");

        accounts.link(first.id(), 999L, AccountIdentity.Verification.OAUTH);

        assertThrows(
                RuntimeException.class, () -> accounts.link(second.id(), 999L, AccountIdentity.Verification.OAUTH));
    }

    @Test
    @DisplayName("A password can be set on an account that signed up without one")
    void setPasswordHash() {
        Account created = accounts.create("nopassword@example.invalid", null);
        assertFalse(accounts.findById(created.id()).orElseThrow().hasPassword());

        accounts.setPasswordHash(created.id(), "fresh-hash");

        Account found = accounts.findById(created.id()).orElseThrow();
        assertTrue(found.hasPassword());
        assertEquals("fresh-hash", found.passwordHash());
    }

    @Test
    @DisplayName("Signing in stamps the account with the time it happened")
    void touchLastLogin() {
        Account created = accounts.create("login@example.invalid", "hash");
        assertNull(created.lastLoginAt());

        accounts.touchLastLogin(created.id());

        assertNotNull(accounts.findById(created.id()).orElseThrow().lastLoginAt());
    }

    @Test
    @DisplayName("Deleting an account takes its Discord link with it")
    void deleteCascadesToLink() {
        Account created = accounts.create("gone@example.invalid", "hash");
        accounts.link(created.id(), 555L, AccountIdentity.Verification.OAUTH);

        accounts.delete(created.id());

        assertTrue(accounts.findById(created.id()).isEmpty());
        assertTrue(accounts.findByDiscordId(555L).isEmpty());
    }

    @Test
    @DisplayName("The handle the OAuth round trip saw is kept with the link")
    void linkRecordsTheHandle() {
        Account account = accounts.create("handle@example.invalid", "hash");
        accounts.link(account.id(), 4001L, AccountIdentity.Verification.OAUTH, "ada");

        AccountIdentity link = accounts.findLinkByAccountId(account.id()).orElseThrow();
        assertEquals("ada", link.handle());
        assertEquals("ada", link.display());
    }

    @Test
    @DisplayName("A link made without a handle shows the id, which is all there is to show")
    void linkWithoutHandleDisplaysTheId() {
        Account account = accounts.create("nohandle@example.invalid", "hash");
        accounts.link(account.id(), 4002L, AccountIdentity.Verification.BOT_DM_CODE);

        AccountIdentity link = accounts.findLinkByAccountId(account.id()).orElseThrow();
        assertNull(link.handle());
        assertEquals("4002", link.display());
    }

    @Test
    @DisplayName("Relinking without a handle keeps the one already known")
    void relinkingKeepsAKnownHandle() {
        Account account = accounts.create("keep@example.invalid", "hash");
        accounts.link(account.id(), 4003L, AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(account.id(), 4003L, AccountIdentity.Verification.BOT_DM_CODE);

        assertEquals(
                "ada", accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("Signing in again under a new handle records the new one")
    void relinkingUpdatesTheHandle() {
        Account account = accounts.create("renamed@example.invalid", "hash");
        accounts.link(account.id(), 4004L, AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(account.id(), 4004L, AccountIdentity.Verification.OAUTH, "ada.lovelace");

        assertEquals(
                "ada.lovelace",
                accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("A handle learned elsewhere is recorded against the id, and only when it changed")
    void rememberHandleUpdatesByDiscordId() {
        Account account = accounts.create("remember@example.invalid", "hash");
        accounts.link(account.id(), 4005L, AccountIdentity.Verification.BOT_DM_CODE);

        assertTrue(accounts.rememberHandle(4005L, "ada"));
        assertFalse(accounts.rememberHandle(4005L, "ada"));
        assertFalse(accounts.rememberHandle(4005L, "  "));
        assertFalse(accounts.rememberHandle(9999L, "nobody"));

        assertEquals(
                "ada", accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("Several ids are named in one go, and an unknown one is simply absent")
    void handlesAreLookedUpInBulk() {
        Account first = accounts.create("bulk-a@example.invalid", "hash");
        Account second = accounts.create("bulk-b@example.invalid", "hash");
        Account third = accounts.create("bulk-c@example.invalid", "hash");
        accounts.link(first.id(), 4101L, AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(second.id(), 4102L, AccountIdentity.Verification.OAUTH, "grace");
        accounts.link(third.id(), 4103L, AccountIdentity.Verification.BOT_DM_CODE);

        var handles = accounts.handles(java.util.List.of(4101L, 4102L, 4103L, 4104L));

        assertEquals(2, handles.size());
        assertEquals("ada", handles.get(4101L));
        assertEquals("grace", handles.get(4102L));
        assertNull(handles.get(4103L));
        assertTrue(accounts.handles(java.util.List.of()).isEmpty());
    }

    @Test
    @DisplayName("A provider nobody has taught us about yet is stored like any other")
    void anyProviderCanBeLinked() {
        Account account = accounts.create("multi@example.invalid", "hash");
        accounts.link(account.id(), AccountIdentity.DISCORD, "5001", AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(account.id(), "github", "octocat-1", AccountIdentity.Verification.OAUTH, "octocat");

        assertEquals(2, accounts.identities(account.id()).size());
        assertEquals(
                "octocat",
                accounts.findIdentity(account.id(), "github").orElseThrow().handle());
        assertEquals(
                account.id(),
                accounts.findByIdentity("github", "octocat-1").orElseThrow().id());
        assertEquals(
                "ada", accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("An account holds one identity per provider, and swapping it replaces the old one")
    void oneIdentityPerProvider() {
        Account account = accounts.create("swap@example.invalid", "hash");
        accounts.link(account.id(), 5101L, AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(account.id(), 5102L, AccountIdentity.Verification.OAUTH, "grace");

        assertEquals(1, accounts.identities(account.id()).size());
        assertEquals(
                "5102", accounts.findLinkByAccountId(account.id()).orElseThrow().externalId());
        assertTrue(accounts.findByIdentity(AccountIdentity.DISCORD, "5101").isEmpty());
    }

    @Test
    @DisplayName("Unlinking one provider leaves the others alone")
    void unlinkIsPerProvider() {
        Account account = accounts.create("unlink@example.invalid", "hash");
        accounts.link(account.id(), AccountIdentity.DISCORD, "5201", AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(account.id(), "github", "octocat-2", AccountIdentity.Verification.OAUTH, "octocat");

        accounts.unlink(account.id());

        assertTrue(accounts.findLinkByAccountId(account.id()).isEmpty());
        assertEquals(
                "octocat",
                accounts.findIdentity(account.id(), "github").orElseThrow().handle());
    }

    @Test
    @DisplayName("The same id at two providers is two identities, not a clash")
    void providersDoNotShareAnIdSpace() {
        Account first = accounts.create("prov-a@example.invalid", "hash");
        Account second = accounts.create("prov-b@example.invalid", "hash");
        accounts.link(first.id(), AccountIdentity.DISCORD, "5301", AccountIdentity.Verification.OAUTH, "ada");
        accounts.link(second.id(), "github", "5301", AccountIdentity.Verification.OAUTH, "grace");

        assertEquals(
                first.id(),
                accounts.findByIdentity(AccountIdentity.DISCORD, "5301")
                        .orElseThrow()
                        .id());
        assertEquals(
                second.id(),
                accounts.findByIdentity("github", "5301").orElseThrow().id());
    }

    @Test
    @DisplayName("Somebody unlinked picks a name and is given four digits with it")
    void chosenNameGetsDiscriminator() {
        Account account = accounts.create("named@example.invalid", "hash");

        String discriminator = accounts.setUsername(account.id(), "ada");

        assertTrue(discriminator.matches("[0-9]{4}"));
        Account named = accounts.findById(account.id()).orElseThrow();
        assertEquals("ada", named.username());
        assertEquals(discriminator, named.discriminator());
        assertEquals("ada#" + discriminator, named.displayName());
    }

    @Test
    @DisplayName("Two people may both be ada, and are told apart by their digits")
    void twoPeopleShareAName() {
        Account first = accounts.create("ada-a@example.invalid", "hash");
        Account second = accounts.create("ada-b@example.invalid", "hash");

        String one = accounts.setUsername(first.id(), "ada");
        String two = accounts.setUsername(second.id(), "ada");

        assertNotEquals(one, two);
        assertEquals(
                first.id(), accounts.findByUsername("ada#" + one).orElseThrow().id());
        assertEquals(
                second.id(), accounts.findByUsername("ada#" + two).orElseThrow().id());
    }

    @Test
    @DisplayName("A name that is nearly full still resolves rather than giving up at random")
    void allocationFallsBackToScanning() {
        for (int taken = 1; taken <= 9998; taken++) {
            Account filler = accounts.create("filler-%d@example.invalid".formatted(taken), "hash");
            writeName(filler.id(), "crowded", "%04d".formatted(taken));
        }
        Account late = accounts.create("late@example.invalid", "hash");

        String discriminator = accounts.setUsername(late.id(), "crowded");

        assertEquals("9999", discriminator);
    }

    @Test
    @DisplayName("A Discord-linked account is named by Discord and cannot be renamed by hand")
    void linkedAccountsAreNamedByDiscord() {
        Account account = accounts.create("linked-name@example.invalid", "hash");
        accounts.link(account.id(), 6001L, AccountIdentity.Verification.OAUTH, "ada");

        Account named = accounts.findById(account.id()).orElseThrow();
        assertEquals("ada", named.username());
        assertNull(named.discriminator());
        assertEquals("ada", named.displayName());

        assertThrows(IllegalStateException.class, () -> accounts.setUsername(account.id(), "someoneelse"));
    }

    @Test
    @DisplayName("A Discord rename carries through to the account's name")
    void discordRenameFollowsThrough() {
        Account account = accounts.create("renamed-name@example.invalid", "hash");
        accounts.link(account.id(), 6002L, AccountIdentity.Verification.OAUTH, "ada");

        accounts.rememberHandle(6002L, "ada.lovelace");

        assertEquals(
                "ada.lovelace", accounts.findById(account.id()).orElseThrow().username());
    }

    @Test
    @DisplayName("Unlinking keeps the name and gives it digits of its own")
    void unlinkingKeepsTheNameWithDigits() {
        Account account = accounts.create("detach@example.invalid", "hash");
        accounts.link(account.id(), 6003L, AccountIdentity.Verification.OAUTH, "ada");

        accounts.unlink(account.id());

        Account detached = accounts.findById(account.id()).orElseThrow();
        assertEquals("ada", detached.username());
        assertNotNull(detached.discriminator());
        assertTrue(detached.displayName().startsWith("ada#"));
    }

    @Test
    @DisplayName("A Discord name is found without digits, and a chosen one needs them")
    void lookupDistinguishesTheTwoKinds() {
        Account linked = accounts.create("lookup-linked@example.invalid", "hash");
        accounts.link(linked.id(), 6004L, AccountIdentity.Verification.OAUTH, "grace");
        Account chosen = accounts.create("lookup-chosen@example.invalid", "hash");
        String discriminator = accounts.setUsername(chosen.id(), "grace");

        assertEquals(linked.id(), accounts.findByUsername("grace").orElseThrow().id());
        assertEquals(
                chosen.id(),
                accounts.findByUsername("grace#" + discriminator).orElseThrow().id());
        assertTrue(accounts.findByUsername("grace#0000").isEmpty());
        assertTrue(accounts.findByUsername("nobody").isEmpty());
    }

    @Test
    @DisplayName("A name nobody may take is refused rather than trimmed into something else")
    void badNamesAreRefused() {
        Account account = accounts.create("badname@example.invalid", "hash");

        for (String bad : List.of("ab", "a b", "ada!", ".ada", "ada.", "", "   ", "a".repeat(33))) {
            assertThrows(
                    IllegalArgumentException.class,
                    () -> accounts.setUsername(account.id(), bad),
                    "should have refused " + bad);
        }
    }

    private static void writeName(int accountId, String username, String discriminator) {
        de.chojo.sadu.queries.api.query.Query.query("UPDATE account SET username = ?, discriminator = ? WHERE id = ?")
                .single(de.chojo.sadu.queries.api.call.Call.call()
                        .bind(username)
                        .bind(discriminator)
                        .bind(accountId))
                .update();
    }
}
