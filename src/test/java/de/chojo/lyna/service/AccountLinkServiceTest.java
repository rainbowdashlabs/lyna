package de.chojo.lyna.service;

import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.data.dao.account.DiscordLink;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The three ways an account comes to carry a Discord id, and what unlinking does to the licenses
 * that id holds.
 */
class AccountLinkServiceTest extends RepositoryTestBase {
    private static final PasswordHasher HASHER = new PasswordHasher();
    private static final long DISCORD_ID = 100200300L;

    @BeforeEach
    void clearAccounts() throws SQLException {
        clear("account_discord_link", "account");
    }

    /**
     * What the OAuth callback decides: an id already linked signs that account in, and an id nobody
     * holds creates the account it will belong to.
     *
     * @return the account the callback signs in, and whether it had to be created
     */
    private Callback callback(long discordId) {
        Optional<Account> existing = accounts.findByDiscordId(discordId);
        if (existing.isPresent()) return new Callback(existing.get(), false);
        Account created = accounts.create(null, null);
        accounts.link(created.id(), discordId, DiscordLink.Verification.OAUTH);
        return new Callback(created, true);
    }

    private record Callback(Account account, boolean created) {
    }

    @Test
    @DisplayName("The first time through OAuth an account is created for the Discord id")
    void oauthFirstSignup() {
        Callback first = callback(DISCORD_ID);

        assertTrue(first.created());
        assertFalse(first.account().hasPassword());
        assertEquals(DISCORD_ID, accounts.findLinkByAccountId(first.account().id()).orElseThrow().discordUserId());
    }

    @Test
    @DisplayName("Coming back through OAuth signs the same account in rather than making another")
    void oauthLoginReusesTheAccount() {
        Callback first = callback(DISCORD_ID);

        Callback second = callback(DISCORD_ID);

        assertFalse(second.created());
        assertEquals(first.account().id(), second.account().id());
    }

    @Test
    @DisplayName("An account that signed up with a password can add Discord afterwards")
    void passwordFirstThenLink() {
        Account created = accounts.create("password-first@example.invalid", HASHER.hash("secret"));

        accounts.link(created.id(), DISCORD_ID, DiscordLink.Verification.OAUTH);

        assertEquals(created.id(), accounts.findByDiscordId(DISCORD_ID).orElseThrow().id());
        assertTrue(accounts.findById(created.id()).orElseThrow().hasPassword());
    }

    @Test
    @DisplayName("A code sent by the bot links just as an OAuth round trip does, and says so")
    void botDmCodeLinks() {
        Account created = accounts.create("no-oauth@example.invalid", HASHER.hash("secret"));

        accounts.link(created.id(), DISCORD_ID, DiscordLink.Verification.BOT_DM_CODE);

        DiscordLink link = accounts.findLinkByAccountId(created.id()).orElseThrow();
        assertEquals("bot_dm_code", link.verifiedVia());
        assertEquals(DISCORD_ID, link.discordUserId());
    }

    @Test
    @DisplayName("Unlinking hides the Discord id, and linking again restores it")
    void unlinkThenRelink() {
        Account created = accounts.create("relinks@example.invalid", HASHER.hash("secret"));
        accounts.link(created.id(), DISCORD_ID, DiscordLink.Verification.OAUTH);

        accounts.unlink(created.id());
        assertTrue(accounts.findByDiscordId(DISCORD_ID).isEmpty());

        accounts.link(created.id(), DISCORD_ID, DiscordLink.Verification.OAUTH);

        assertEquals(created.id(), accounts.findByDiscordId(DISCORD_ID).orElseThrow().id());
    }

    @Test
    @DisplayName("A Discord id another account already holds cannot be taken over")
    void discordIdCannotBeTakenOver() {
        Account holder = accounts.create("holder@example.invalid", HASHER.hash("secret"));
        accounts.link(holder.id(), DISCORD_ID, DiscordLink.Verification.OAUTH);
        Account newcomer = accounts.create("newcomer@example.invalid", HASHER.hash("secret"));

        assertThrowsOnLink(newcomer.id());

        assertEquals(holder.id(), accounts.findByDiscordId(DISCORD_ID).orElseThrow().id());
    }

    private static void assertThrowsOnLink(int accountId) {
        try {
            accounts.link(accountId, DISCORD_ID, DiscordLink.Verification.OAUTH);
            throw new AssertionError("Linking an already held Discord id should have been refused");
        } catch (RuntimeException expected) {
            // the unique constraint on the link table is what refuses it
        }
    }
}
