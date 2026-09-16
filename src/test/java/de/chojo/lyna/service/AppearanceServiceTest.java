package de.chojo.lyna.service;

import de.chojo.lyna.data.dao.InstanceSettings;
import de.chojo.lyna.data.dao.account.Account;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * How an account's own look is resolved: what it chose, falling back to what the operator set, and
 * what the operator's policy refuses to let it choose at all.
 */
class AppearanceServiceTest extends RepositoryTestBase {
    private Account account;

    @BeforeEach
    void freshAccount() throws SQLException {
        clear("account_identity", "account");
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, true, true, List.of(), null));
        account = accounts.create("appearance@example.invalid", "hash");
    }

    /**
     * The policy the endpoint applies, kept here so what the test asserts is the rule rather than
     * the wiring around it.
     *
     * @return what is actually stored for the account after the choice is offered
     */
    private Stored apply(String theme, String feel, String darkMode) {
        InstanceSettings policy = instanceSettings.get();
        Account current = accounts.findById(account.id()).orElseThrow();

        String nextTheme = current.theme();
        if (policy.allowUserTheme() && theme != null
                && (policy.enabledThemes().isEmpty() || policy.enabledThemes().contains(theme))) {
            nextTheme = theme.isBlank() ? null : theme;
        }
        String nextFeel = current.feel();
        if (policy.allowUserFeel() && !policy.lockFeel() && feel != null) {
            nextFeel = feel.isBlank() ? null : feel;
        }
        String nextDarkMode = darkMode == null
                ? current.darkMode()
                : darkMode.isBlank() ? null : darkMode;

        accounts.setAppearance(account.id(), nextTheme, nextFeel, nextDarkMode);
        Account stored = accounts.findById(account.id()).orElseThrow();
        return new Stored(stored.theme(), stored.feel(), stored.darkMode());
    }

    private record Stored(String theme, String feel, String darkMode) {
    }

    @Test
    @DisplayName("A fresh account has chosen nothing, so it follows the operator's defaults")
    void freshAccountChoosesNothing() {
        Account stored = accounts.findById(account.id()).orElseThrow();

        assertNull(stored.theme());
        assertNull(stored.feel());
        assertNull(stored.darkMode());
    }

    @Test
    @DisplayName("What the account picks is what it gets back")
    void choicesAreStored() {
        Stored stored = apply("midnight", "CORNERS", "dark");

        assertEquals("midnight", stored.theme());
        assertEquals("CORNERS", stored.feel());
        assertEquals("dark", stored.darkMode());
    }

    @Test
    @DisplayName("Clearing a choice puts the account back on the operator's default")
    void blankClearsTheChoice() {
        apply("midnight", "CORNERS", "dark");

        Stored stored = apply("", "", "");

        assertNull(stored.theme());
        assertNull(stored.feel());
        assertNull(stored.darkMode());
    }

    @Test
    @DisplayName("Leaving a field out changes nothing about it")
    void absentFieldsAreLeftAlone() {
        apply("midnight", "CORNERS", "dark");

        Stored stored = apply(null, null, "light");

        assertEquals("midnight", stored.theme());
        assertEquals("CORNERS", stored.feel());
        assertEquals("light", stored.darkMode());
    }

    @Test
    @DisplayName("A theme outside the operator's whitelist is not taken")
    void themeOutsideTheWhitelistIsRefused() {
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, true, true, List.of("lyna", "forest"), null));

        assertNull(apply("midnight", null, null).theme());
        assertEquals("forest", apply("forest", null, null).theme());
    }

    @Test
    @DisplayName("With the theme forced for everyone, the account's pick is ignored")
    void lockedThemeIgnoresThePick() {
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, false, true, List.of(), null));

        assertNull(apply("midnight", null, null).theme());
    }

    @Test
    @DisplayName("With the feel locked, the account's pick is ignored but its theme still lands")
    void lockedFeelIgnoresThePick() {
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", true, true, true, List.of(), null));

        Stored stored = apply("midnight", "CORNERS", null);

        assertEquals("midnight", stored.theme());
        assertNull(stored.feel());
    }

    @Test
    @DisplayName("Dark mode is the account's own, whatever the operator locked")
    void darkModeIsAlwaysTheAccountsOwn() {
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", true, false, false, List.of(), null));

        assertEquals("dark", apply(null, null, "dark").darkMode());
    }
}
