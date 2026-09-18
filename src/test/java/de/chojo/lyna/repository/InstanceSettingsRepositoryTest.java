/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.data.dao.InstanceSettings;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class InstanceSettingsRepositoryTest extends RepositoryTestBase {

    /**
     * There is one settings row and every test in here writes to it, so without this the tests read
     * each other's leavings - which is how the one below passed for as long as the value it asserted
     * happened to be the value another test wrote.
     */
    @BeforeEach
    void freshRow() throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            statement.execute("DELETE FROM %s.instance_settings".formatted(schemaName));
            statement.execute("INSERT INTO %s.instance_settings (id) VALUES (1)".formatted(schemaName));
        }
    }

    @Test
    @DisplayName("A fresh instance defaults to the scheme the interface is drawn around")
    void migrationSeedsTheRow() {
        InstanceSettings settings = instanceSettings.get();

        assertEquals("transistor", settings.defaultTheme());
        assertTrue(settings.allowUserTheme());
        assertTrue(settings.enabledThemes().isEmpty());
        assertNull(settings.customThemeColorsJson());
    }

    @Test
    @DisplayName("Every field survives a write and a read, the array and the JSON included")
    void updateRoundTrips() {
        instanceSettings.update(new InstanceSettings(
                "midnight", false, List.of("lyna", "midnight"), "{\"light\":{\"primary\":\"#123456\"}}"));

        InstanceSettings settings = instanceSettings.get();
        assertEquals("midnight", settings.defaultTheme());
        assertFalse(settings.allowUserTheme());
        assertEquals(List.of("lyna", "midnight"), settings.enabledThemes());
        assertTrue(settings.customThemeColorsJson().contains("#123456"));
    }

    @Test
    @DisplayName("Clearing the theme whitelist means every theme is available again")
    void enabledThemesCanBeEmptied() {
        instanceSettings.update(new InstanceSettings("lyna", true, List.of("lyna"), null));
        assertEquals(List.of("lyna"), instanceSettings.get().enabledThemes());

        instanceSettings.update(new InstanceSettings("lyna", true, List.of(), null));

        assertTrue(instanceSettings.get().enabledThemes().isEmpty());
    }

    @Test
    @DisplayName("Custom colours can be taken away again")
    void customColoursCanBeCleared() {
        instanceSettings.update(new InstanceSettings("lyna", true, List.of(), "{\"light\":{}}"));
        assertNotNull(instanceSettings.get().customThemeColorsJson());

        instanceSettings.update(new InstanceSettings("lyna", true, List.of(), null));

        assertNull(instanceSettings.get().customThemeColorsJson());
    }
}
