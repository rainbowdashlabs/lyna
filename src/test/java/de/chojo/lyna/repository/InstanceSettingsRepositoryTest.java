package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.InstanceSettings;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceSettingsRepositoryTest extends RepositoryTestBase {

    @Test
    @DisplayName("A fresh instance already carries its single settings row")
    void migrationSeedsTheRow() {
        InstanceSettings settings = instanceSettings.get();

        assertNotNull(settings.defaultTheme());
        assertNotNull(settings.defaultFeel());
        assertTrue(settings.allowUserTheme());
        assertTrue(settings.allowUserFeel());
        assertFalse(settings.lockFeel());
        assertTrue(settings.enabledThemes().isEmpty());
        assertNull(settings.customThemeColorsJson());
    }

    @Test
    @DisplayName("Every field survives a write and a read, the array and the JSON included")
    void updateRoundTrips() {
        instanceSettings.update(new InstanceSettings(
                "midnight", "CORNERS", true, false, false,
                List.of("lyna", "midnight"), "{\"light\":{\"primary\":\"#123456\"}}"));

        InstanceSettings settings = instanceSettings.get();
        assertEquals("midnight", settings.defaultTheme());
        assertEquals("CORNERS", settings.defaultFeel());
        assertTrue(settings.lockFeel());
        assertFalse(settings.allowUserTheme());
        assertFalse(settings.allowUserFeel());
        assertEquals(List.of("lyna", "midnight"), settings.enabledThemes());
        assertTrue(settings.customThemeColorsJson().contains("#123456"));
    }

    @Test
    @DisplayName("Clearing the theme whitelist means every theme is available again")
    void enabledThemesCanBeEmptied() {
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, true, true, List.of("lyna"), null));
        assertEquals(List.of("lyna"), instanceSettings.get().enabledThemes());

        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, true, true, List.of(), null));

        assertTrue(instanceSettings.get().enabledThemes().isEmpty());
    }

    @Test
    @DisplayName("Custom colours can be taken away again")
    void customColoursCanBeCleared() {
        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, true, true, List.of(), "{\"light\":{}}"));
        assertNotNull(instanceSettings.get().customThemeColorsJson());

        instanceSettings.update(new InstanceSettings(
                "lyna", "ROUNDED", false, true, true, List.of(), null));

        assertNull(instanceSettings.get().customThemeColorsJson());
    }
}
