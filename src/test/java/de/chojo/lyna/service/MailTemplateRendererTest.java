/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.mail.MailTemplateRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What comes out of the mail templates, which is the only place the rendering is checked: a mail is
 * seen once, by somebody who is not looking for bugs in it.
 */
class MailTemplateRendererTest {
    private final MailTemplateRenderer renderer = new MailTemplateRenderer("Lyna", "https://example.invalid");

    @Test
    @DisplayName("A rendered mail carries the layout's chrome around its own body")
    void bodyIsRenderedIntoTheLayout() {
        String html = renderer.render("reset-password", "en", Map.of("url", "https://example.invalid/reset"));

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("Password reset"));
        assertTrue(html.contains("https://example.invalid/reset"));
        assertTrue(html.contains("</html>"));
    }

    @Test
    @DisplayName("Every variable a template names is filled in")
    void variablesAreSubstituted() {
        String html = renderer.render(
                "licence-issued",
                "en",
                Map.of(
                        "name", "Ada",
                        "product", "Chatty",
                        "key", "ABCD-1234"));

        assertTrue(html.contains("Ada"));
        assertTrue(html.contains("Chatty"));
        assertTrue(html.contains("ABCD-1234"));
    }

    @Test
    @DisplayName("A part of a template nothing was given for is left out rather than left empty")
    void optionalBlocksAreOmitted() {
        String without =
                renderer.render("licence-issued", "en", Map.of("name", "Ada", "product", "Chatty", "key", "ABCD-1234"));
        assertFalse(without.contains("Download Chatty"));

        String with = renderer.render(
                "licence-issued",
                "en",
                Map.of(
                        "name",
                        "Ada",
                        "product",
                        "Chatty",
                        "key",
                        "ABCD-1234",
                        "downloadUrl",
                        "https://example.invalid/d"));
        assertTrue(with.contains("Download Chatty"));
    }

    @Test
    @DisplayName("What a variable carries is escaped, so a product name cannot write markup")
    void variablesAreEscaped() {
        String html = renderer.render(
                "licence-issued",
                "en",
                Map.of(
                        "name", "Ada",
                        "product", "<script>alert(1)</script>",
                        "key", "ABCD-1234"));

        assertFalse(html.contains("<script>alert(1)</script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("Subjects come from the catalogue, with their placeholders filled in")
    void subjectsAreLookedUp() {
        assertEquals("Your Chatty licence", renderer.subject("licence-issued", "en", Map.of("product", "Chatty")));
        assertEquals(
                "Ada shared their Chatty licence with you",
                renderer.subject("licence-shared", "en", Map.of("owner", "Ada", "product", "Chatty")));
    }

    @Test
    @DisplayName("A subject nobody has written answers with its key rather than failing")
    void unknownSubjectFallsBackToItsKey() {
        assertEquals("no-such-mail", renderer.subject("no-such-mail", "en", Map.of()));
    }

    @Test
    @DisplayName("A locale that has no template for this mail falls back to English")
    void unknownLocaleFallsBackToEnglish() {
        String html = renderer.render("reset-password", "de", Map.of("url", "https://example.invalid/reset"));

        assertTrue(html.contains("Password reset"));
        assertTrue(html.contains("lang=\"en\""));
    }

    @Test
    @DisplayName("The per-product body is placed inside the layout rather than sent bare")
    void customBodyIsWrappedInTheLayout() {
        String html = renderer.render(
                "licence-custom", "en", Map.of("body", "<p>Anything the operator wrote</p>", "senderName", "Lyna"));

        assertTrue(html.startsWith("<!DOCTYPE html>"));
        assertTrue(html.contains("<p>Anything the operator wrote</p>"));
    }

    @Test
    @DisplayName("Every mail the application sends renders")
    void everyTemplateRenders() {
        Map<String, Object> everything = Map.of(
                "name",
                "Ada",
                "product",
                "Chatty",
                "key",
                "ABCD-1234",
                "owner",
                "Grace",
                "url",
                "https://example.invalid/u",
                "licencesUrl",
                "https://example.invalid/l",
                "downloadUrl",
                "https://example.invalid/d");

        for (String template : new String[] {"licence-issued", "reset-password", "licence-shared", "licence-revoked"}) {
            String html = renderer.render(template, "en", everything);
            assertTrue(html.contains("</html>"), template + " did not render");
            assertFalse(html.contains("{{"), template + " left a placeholder unrendered");
        }
    }
}
