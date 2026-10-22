/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PluginManifestTest {
    private static PluginManifest range(String min, String max) {
        return new PluginManifest("P", "1.0.0", List.of(), min, max, null, List.of(), Map.of());
    }

    @Test
    @DisplayName("A bound covers the versions it is a prefix of, inclusively on both ends")
    void bounds() {
        PluginManifest manifest = range("1.20", "1.21");

        assertTrue(manifest.supports("1.20"));
        assertTrue(manifest.supports("1.20.4"));
        assertTrue(manifest.supports("1.21.4"));
        assertFalse(manifest.supports("1.19.4"));
        assertFalse(manifest.supports("1.22"));
    }

    @Test
    @DisplayName("Exact bounds are exact, and a missing bound covers everything on its side")
    void exactAndOpen() {
        assertTrue(range("1.20.4", "1.21.4").supports("1.21.4"));
        assertFalse(range("1.20.4", "1.21.4").supports("1.21.5"));
        assertFalse(range("1.20.4", "1.21.4").supports("1.20.3"));
        assertTrue(range("1.20.4", null).supports("2.0"));
        assertTrue(range(null, "1.21").supports("1.8.8"));
        assertTrue(range(null, null).supports("1.0"));
    }

    @Test
    @DisplayName("Parts that are not plain numbers compare by their leading digits")
    void oddParts() {
        assertTrue(range("1.20", "1.21").supports("1.21-pre1"));
        assertTrue(range("1.20", null).supports("1.20.x"));
        assertFalse(range("1.20", null).supports("1"));
    }
}
