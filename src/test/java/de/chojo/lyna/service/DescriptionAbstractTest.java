/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.kiosk.service.DescriptionAbstract;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the storefront list says about a product in a line or two.
 */
class DescriptionAbstractTest {

    @Test
    @DisplayName("Nothing written is no abstract, and neither is a description of only headings and images")
    void nothing() {
        assertNull(DescriptionAbstract.of(null));
        assertNull(DescriptionAbstract.of(""));
        assertNull(DescriptionAbstract.of("# Title\n\n![logo](logo.png)\n\n> a quote\n\n```\ncode\n```"));
    }

    @Test
    @DisplayName("The first prose paragraph is taken, its markdown read as plain text")
    void firstProse() {
        String markdown = """
                # Chatty

                [![build](https://ci/badge.svg)](https://ci) <img src="x.png">

                A **chat** plugin with [channels](https://example.invalid/channels) and `commands`,
                for _every_ server.

                A second paragraph nobody sees here.
                """;

        assertEquals("A chat plugin with channels and commands, for every server.", DescriptionAbstract.of(markdown));
    }

    @Test
    @DisplayName("A long paragraph is cut at a word, with an ellipsis")
    void cutAtAWord() {
        String text = DescriptionAbstract.of("word ".repeat(100));

        assertTrue(text.length() <= DescriptionAbstract.LIMIT);
        assertTrue(text.endsWith("word…"));
    }

    @Test
    @DisplayName("A paragraph without spaces is cut where the limit falls")
    void cutWithoutSpaces() {
        String text = DescriptionAbstract.of("x".repeat(300));

        assertEquals(DescriptionAbstract.LIMIT, text.length());
        assertTrue(text.endsWith("…"));
    }
}
