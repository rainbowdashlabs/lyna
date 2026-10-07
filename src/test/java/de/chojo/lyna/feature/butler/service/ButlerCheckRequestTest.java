/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.butler.service;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The two shapes of update check that plugins built against UpdateButler send, told apart from Lyna's
 * own.
 */
class ButlerCheckRequestTest {

    private static Map<String, List<String>> query(String... pairs) {
        Map<String, List<String>> query = new LinkedHashMap<>();
        for (int i = 0; i < pairs.length; i += 2) {
            query.put(pairs[i], List.of(pairs[i + 1]));
        }
        return query;
    }

    @Test
    @DisplayName("The old shape is read with its dev flag")
    void oldShape() {
        var request = ButlerCheckRequest.wellFormed(query("id", "5", "version", "1.2.3", "devbuild", "true"));

        assertEquals(Optional.of(new ButlerCheckRequest(5, "1.2.3", true)), request);
    }

    @Test
    @DisplayName("The old shape without a dev flag follows stable")
    void oldShapeWithoutFlag() {
        var request = ButlerCheckRequest.wellFormed(query("id", "5", "version", "1.2.3"));

        assertEquals(Optional.of(new ButlerCheckRequest(5, "1.2.3", false)), request);
    }

    @Test
    @DisplayName("The newer shape carries its version in a parameter's name")
    void malformedShape() {
        var request = ButlerCheckRequest.malformed(query("version1.2.3-DEV", "", "id", "5"));

        assertEquals(Optional.of(new ButlerCheckRequest(5, "1.2.3-DEV", false)), request);
    }

    @Test
    @DisplayName("Lyna's own check is not taken for Butler's")
    void lynaCheckIsNotButler() {
        assertTrue(ButlerCheckRequest.malformed(query("id", "5", "version", "1.2.3")).isEmpty());
        assertTrue(ButlerCheckRequest.malformed(query("id", "5", "version", "1.2.3", "versions", "x")).isEmpty());
    }

    @Test
    @DisplayName("A check without a usable id or version is nobody's")
    void incompleteIsNothing() {
        assertTrue(ButlerCheckRequest.malformed(query("version1.2.3", "")).isEmpty());
        assertTrue(ButlerCheckRequest.malformed(query("version1.2.3", "", "id", "five")).isEmpty());
        assertTrue(ButlerCheckRequest.wellFormed(query("id", "5", "version", " ")).isEmpty());
        assertTrue(ButlerCheckRequest.wellFormed(query("id", "5")).isEmpty());
    }
}
