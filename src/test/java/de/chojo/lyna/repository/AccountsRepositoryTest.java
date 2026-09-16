package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.DiscordLink;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class AccountsRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void clearAccounts() throws SQLException {
        clear("account_discord_link", "account");
    }

    @Test
    @DisplayName("An account is found again by its id and by its email")
    void createAndFind() {
        Account created = accounts.create("someone@example.invalid", "hash");

        assertTrue(created.id() > 0);
        assertEquals("someone@example.invalid", created.email());
        assertNotNull(created.createdAt());

        assertEquals(created.id(), accounts.findById(created.id()).orElseThrow().id());
        assertEquals(created.id(), accounts.findByEmail("someone@example.invalid").orElseThrow().id());
    }

    @Test
    @DisplayName("Email lookup ignores the case the address was typed in")
    void findByEmailIsCaseInsensitive() {
        Account created = accounts.create("Mixed.Case@example.invalid", "hash");

        assertEquals(created.id(), accounts.findByEmail("mixed.case@example.invalid").orElseThrow().id());
        assertEquals(created.id(), accounts.findByEmail("MIXED.CASE@EXAMPLE.INVALID").orElseThrow().id());
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

        accounts.link(created.id(), 1234567890L, DiscordLink.Verification.OAUTH);

        assertEquals(created.id(), accounts.findByDiscordId(1234567890L).orElseThrow().id());
        DiscordLink link = accounts.findLinkByAccountId(created.id()).orElseThrow();
        assertEquals(1234567890L, link.discordUserId());
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

        accounts.link(created.id(), 111L, DiscordLink.Verification.OAUTH);
        accounts.link(created.id(), 222L, DiscordLink.Verification.BOT_DM_CODE);

        assertTrue(accounts.findByDiscordId(111L).isEmpty());
        assertEquals(created.id(), accounts.findByDiscordId(222L).orElseThrow().id());
        assertEquals("bot_dm_code", accounts.findLinkByAccountId(created.id()).orElseThrow().verifiedVia());
    }

    @Test
    @DisplayName("A Discord id belongs to one account at a time")
    void discordIdIsUnique() {
        Account first = accounts.create("first@example.invalid", "hash");
        Account second = accounts.create("second@example.invalid", "hash");

        accounts.link(first.id(), 999L, DiscordLink.Verification.OAUTH);

        assertThrows(RuntimeException.class,
                () -> accounts.link(second.id(), 999L, DiscordLink.Verification.OAUTH));
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
        accounts.link(created.id(), 555L, DiscordLink.Verification.OAUTH);

        accounts.delete(created.id());

        assertTrue(accounts.findById(created.id()).isEmpty());
        assertTrue(accounts.findByDiscordId(555L).isEmpty());
    }

    @Test
    @DisplayName("The handle the OAuth round trip saw is kept with the link")
    void linkRecordsTheHandle() {
        Account account = accounts.create("handle@example.invalid", "hash");
        accounts.link(account.id(), 4001L, DiscordLink.Verification.OAUTH, "ada");

        DiscordLink link = accounts.findLinkByAccountId(account.id()).orElseThrow();
        assertEquals("ada", link.handle());
        assertEquals("ada", link.display());
    }

    @Test
    @DisplayName("A link made without a handle shows the id, which is all there is to show")
    void linkWithoutHandleDisplaysTheId() {
        Account account = accounts.create("nohandle@example.invalid", "hash");
        accounts.link(account.id(), 4002L, DiscordLink.Verification.BOT_DM_CODE);

        DiscordLink link = accounts.findLinkByAccountId(account.id()).orElseThrow();
        assertNull(link.handle());
        assertEquals("4002", link.display());
    }

    @Test
    @DisplayName("Relinking without a handle keeps the one already known")
    void relinkingKeepsAKnownHandle() {
        Account account = accounts.create("keep@example.invalid", "hash");
        accounts.link(account.id(), 4003L, DiscordLink.Verification.OAUTH, "ada");
        accounts.link(account.id(), 4003L, DiscordLink.Verification.BOT_DM_CODE);

        assertEquals("ada", accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("Signing in again under a new handle records the new one")
    void relinkingUpdatesTheHandle() {
        Account account = accounts.create("renamed@example.invalid", "hash");
        accounts.link(account.id(), 4004L, DiscordLink.Verification.OAUTH, "ada");
        accounts.link(account.id(), 4004L, DiscordLink.Verification.OAUTH, "ada.lovelace");

        assertEquals("ada.lovelace", accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("A handle learned elsewhere is recorded against the id, and only when it changed")
    void rememberHandleUpdatesByDiscordId() {
        Account account = accounts.create("remember@example.invalid", "hash");
        accounts.link(account.id(), 4005L, DiscordLink.Verification.BOT_DM_CODE);

        assertTrue(accounts.rememberHandle(4005L, "ada"));
        assertFalse(accounts.rememberHandle(4005L, "ada"));
        assertFalse(accounts.rememberHandle(4005L, "  "));
        assertFalse(accounts.rememberHandle(9999L, "nobody"));

        assertEquals("ada", accounts.findLinkByAccountId(account.id()).orElseThrow().handle());
    }

    @Test
    @DisplayName("Several ids are named in one go, and an unknown one is simply absent")
    void handlesAreLookedUpInBulk() {
        Account first = accounts.create("bulk-a@example.invalid", "hash");
        Account second = accounts.create("bulk-b@example.invalid", "hash");
        Account third = accounts.create("bulk-c@example.invalid", "hash");
        accounts.link(first.id(), 4101L, DiscordLink.Verification.OAUTH, "ada");
        accounts.link(second.id(), 4102L, DiscordLink.Verification.OAUTH, "grace");
        accounts.link(third.id(), 4103L, DiscordLink.Verification.BOT_DM_CODE);

        var handles = accounts.handles(java.util.List.of(4101L, 4102L, 4103L, 4104L));

        assertEquals(2, handles.size());
        assertEquals("ada", handles.get(4101L));
        assertEquals("grace", handles.get(4102L));
        assertNull(handles.get(4103L));
        assertTrue(accounts.handles(java.util.List.of()).isEmpty());
    }
}
