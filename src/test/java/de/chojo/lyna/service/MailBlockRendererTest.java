/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.mail.entity.MailLink;
import de.chojo.lyna.mail.blocks.DefaultPurchaseMail;
import de.chojo.lyna.mail.blocks.MailBlockRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an operator's blocks turn into.
 *
 * <p>Half of this is about escaping, because the blocks are written by somebody through a form and
 * the result is sent under the application's name to somebody else's inbox.
 */
class MailBlockRendererTest {
    private final MailBlockRenderer renderer = new MailBlockRenderer();

    private static final Map<String, Object> VALUES = Map.of(
            "name", "Ada",
            "key", "ABCD-1234",
            "product", "Chatty",
            "downloadUrl", "https://example.invalid/d");

    private String render(String blocks) {
        return renderer.render(blocks, VALUES);
    }

    @Test
    @DisplayName("Each block type becomes the markup the layout styles")
    void blockTypesRender() {
        String html = render("""
                [{"type":"heading","text":"Welcome"},
                 {"type":"paragraph","text":"Thanks for buying"},
                 {"type":"divider"}]
                """);

        assertTrue(html.contains("<h2"));
        assertTrue(html.contains("Welcome"));
        assertTrue(html.contains("<p"));
        assertTrue(html.contains("<hr"));
    }

    @Test
    @DisplayName("Placeholders are filled in from the values given")
    void placeholdersAreSubstituted() {
        String html = render("""
                [{"type":"paragraph","text":"Hi {{ name }}, here is {{ product }}"}]
                """);

        assertTrue(html.contains("Hi Ada, here is Chatty"));
    }

    @Test
    @DisplayName("The key block shows the licence key under its own label")
    void keyBlockRendersTheKey() {
        String html = render("""
                [{"type":"key","label":"Your key"}]
                """);

        assertTrue(html.contains("Your key"));
        assertTrue(html.contains("ABCD-1234"));
        assertTrue(html.contains("<code"));
    }

    @Test
    @DisplayName("A list becomes a list")
    void listRenders() {
        String html = render("""
                [{"type":"list","items":["First","Second"]}]
                """);

        assertTrue(html.contains("<ul"));
        assertTrue(html.contains("<li>First</li>"));
        assertTrue(html.contains("<li>Second</li>"));
    }

    @Test
    @DisplayName("An operator writes a link with brackets, and gets one")
    void operatorLinksAreRendered() {
        String html = render("""
                [{"type":"paragraph","text":"Read the [docs](https://example.invalid/docs) first"}]
                """);

        assertTrue(html.contains("<a style=\"color:#E92063;\" href=\"https://example.invalid/docs\">docs</a>"));
    }

    @Test
    @DisplayName("Markup an operator typed is shown as text, not obeyed")
    void operatorTextIsEscaped() {
        String html = render("""
                [{"type":"paragraph","text":"<script>alert(1)</script>"}]
                """);

        assertFalse(html.contains("<script>"));
        assertTrue(html.contains("&lt;script&gt;"));
    }

    @Test
    @DisplayName("Markup inside a value is shown as text too")
    void valuesAreEscaped() {
        String html = renderer.render("""
                [{"type":"paragraph","text":"Hi {{ name }}"}]
                """, Map.of("name", "<img src=x onerror=alert(1)>"));

        assertFalse(html.contains("<img"));
        assertTrue(html.contains("&lt;img"));
    }

    @Test
    @DisplayName("A value cannot make a link, however it is spelled")
    void valuesCannotBecomeLinks() {
        String html = renderer.render("""
                [{"type":"paragraph","text":"Hi {{ name }}"}]
                """, Map.of("name", "[click](javascript:alert(1))"));

        assertFalse(html.contains("<a "));
        assertTrue(html.contains("[click]"));
    }

    @Test
    @DisplayName("A button only points somewhere on the web")
    void buttonUrlMustBeHttp() {
        assertTrue(render("""
                [{"type":"button","label":"Get it","url":"https://example.invalid/x"}]
                """).contains("href=\"https://example.invalid/x\""));

        assertEquals("", render("""
                [{"type":"button","label":"Get it","url":"javascript:alert(1)"}]
                """));
        assertEquals("", render("""
                [{"type":"button","label":"Get it","url":"data:text/html,<script>alert(1)</script>"}]
                """));
    }

    @Test
    @DisplayName("A button whose address stands for nothing is left out rather than led to nowhere")
    void buttonWithoutAnAddressIsOmitted() {
        String html = renderer.render("""
                [{"type":"button","label":"Download","url":"{{ downloadUrl }}"}]
                """, Map.of("downloadUrl", ""));

        assertEquals("", html);
    }

    @Test
    @DisplayName("A button's address can come from a placeholder")
    void buttonUrlCanBeAPlaceholder() {
        String html = render("""
                [{"type":"button","label":"Download {{ product }}","url":"{{ downloadUrl }}"}]
                """);

        assertTrue(html.contains("href=\"https://example.invalid/d\""));
        assertTrue(html.contains("Download Chatty"));
    }

    @Test
    @DisplayName("The raw block is HTML on purpose, which is what carries an old template across")
    void rawBlockIsNotEscaped() {
        String html = render("""
                [{"type":"raw","html":"<p class='old'>Written before blocks existed</p>"}]
                """);

        assertTrue(html.contains("<p class='old'>Written before blocks existed</p>"));
    }

    @Test
    @DisplayName("A block type nobody knows is left out rather than guessed at")
    void unknownBlockIsSkipped() {
        assertEquals("", render("""
                [{"type":"carousel","text":"nope"}]
                """));
    }

    @Test
    @DisplayName("Nothing composed yet renders as nothing")
    void emptyRendersEmpty() {
        assertEquals("", renderer.render(null, VALUES));
        assertEquals("", renderer.render("", VALUES));
        assertEquals("", renderer.render("[]", VALUES));
    }

    @Test
    @DisplayName("Something that is not a block document says so rather than rendering oddly")
    void malformedIsRejected() {
        assertThrows(IllegalArgumentException.class, () -> renderer.render("not json", VALUES));
        assertThrows(IllegalArgumentException.class, () -> renderer.render("{\"type\":\"heading\"}", VALUES));
    }

    @Test
    @DisplayName("A preview has something to show for every placeholder")
    void sampleValuesCoverThePlaceholders() {
        Map<String, Object> sample = MailBlockRenderer.sampleValues("Chatty");

        String html = renderer.render("""
                [{"type":"paragraph","text":"{{ name }} {{ product }}"},
                 {"type":"key","label":"Key"},
                 {"type":"button","label":"Go","url":"{{ downloadUrl }}"}]
                """, sample);

        assertTrue(html.contains("Ada Lovelace"));
        assertTrue(html.contains("Chatty"));
        assertTrue(html.contains("LYNA-DEMO-KEY-0000"));
        assertTrue(html.contains("href=\"https://example.invalid/download\""));
    }

    private static Map<String, Object> with(Object... pairs) {
        Map<String, Object> values = new java.util.LinkedHashMap<>(VALUES);
        for (int i = 0; i < pairs.length; i += 2) values.put((String) pairs[i], pairs[i + 1]);
        return values;
    }

    @Test
    @DisplayName("A link can point at a placeholder, and becomes a link once the value is an address")
    void placeholderLinksResolve() {
        String html = renderer.render("""
                [{"type":"paragraph","text":"Read the [wiki]({{ wikiUrl }})"}]
                """, with("wikiUrl", "https://example.invalid/wiki?a=1&b=2"));

        assertTrue(html.contains("href=\"https://example.invalid/wiki?a=1&amp;b=2\">wiki</a>"));
    }

    @Test
    @DisplayName("A paragraph whose every placeholder link is empty is left out")
    void paragraphWithOnlyEmptyLinksIsSkipped() {
        assertEquals("", renderer.render("""
                [{"type":"paragraph","text":"Read the [wiki]({{ wikiUrl }})"}]
                """, with("wikiUrl", "")));
    }

    @Test
    @DisplayName("A paragraph with one link left keeps the others as their label")
    void partlyEmptyLinksKeepTheirLabel() {
        String html = renderer.render("""
                [{"type":"paragraph","text":"On [GitHub]({{ productUrl }}) or [Discord]({{ discordUrl }})"}]
                """, with("productUrl", "https://example.invalid/src", "discordUrl", null));

        assertTrue(html.contains(">GitHub</a>"));
        assertTrue(html.contains("or Discord"));
        assertFalse(html.contains(">Discord</a>"));
    }

    @Test
    @DisplayName("A placeholder link is only made to somewhere on the web")
    void placeholderLinksMustBeHttp() {
        String html = renderer.render("""
                [{"type":"paragraph","text":"See [this]({{ wikiUrl }}) and more"}]
                """, with("wikiUrl", "javascript:alert(1)"));

        assertEquals("", html, "its only link came up empty, so the paragraph goes");
    }

    @Test
    @DisplayName("A heading and a list item go the same way as a paragraph")
    void headingsAndItemsAreSkippedToo() {
        Map<String, Object> values = with("wikiUrl", "");
        assertEquals("", renderer.render("""
                [{"type":"heading","text":"[Docs]({{ wikiUrl }})"}]
                """, values));

        String list = renderer.render("""
                [{"type":"list","items":["[Docs]({{ wikiUrl }})","Plain"]}]
                """, values);
        assertFalse(list.contains("Docs"));
        assertTrue(list.contains("<li>Plain</li>"));

        assertEquals("", renderer.render("""
                [{"type":"list","items":["[Docs]({{ wikiUrl }})"]}]
                """, values), "a list with nothing left is no list");
    }

    @Test
    @DisplayName("A block that requires a placeholder is left out when it stands for nothing, zero included")
    void requiresSkipsTheBlock() {
        String blocks = """
                [{"type":"paragraph","text":"Share with {{ shares }}","requires":["shares"]}]
                """;

        assertTrue(renderer.render(blocks, with("shares", 2)).contains("Share with 2"));
        assertEquals("", renderer.render(blocks, with("shares", 0)));
        assertEquals("", renderer.render(blocks, with("shares", null)));
        assertEquals("", renderer.render(blocks, with("shares", "false")));
    }

    @Test
    @DisplayName("A paragraph can be centred and set in italics")
    void paragraphAlignAndEmphasis() {
        String html = render("""
                [{"type":"paragraph","text":"Thanks","align":"center","emphasis":true}]
                """);

        assertTrue(html.contains("text-align:center;"));
        assertTrue(html.contains("<em>Thanks</em>"));
    }

    @Test
    @DisplayName("An image shows from an address, and not at all without one")
    void imageRenders() {
        String blocks = """
                [{"type":"image","url":"{{ productIcon }}","alt":"{{ product }}"}]
                """;

        String html = renderer.render(blocks, with("productIcon", "https://example.invalid/i.png"));
        assertTrue(html.contains("<img src=\"https://example.invalid/i.png\" alt=\"Chatty\""));
        assertEquals("", renderer.render(blocks, with("productIcon", null)));
        assertEquals("", renderer.render(blocks, with("productIcon", "data:image/png;base64,AA")));
    }

    @Test
    @DisplayName("The links block shows the guild's links in a row, and only those somewhere on the web")
    void linksBlockRenders() {
        String html = renderer.render(
                """
                [{"type":"links"}]
                """,
                with(
                        MailBlockRenderer.LINKS,
                        List.of(
                                new MailLink("GitHub", "https://example.invalid/gh"),
                                new MailLink("<b>Bad</b>", "javascript:alert(1)"),
                                new MailLink("Patreon", "https://example.invalid/p"))));

        assertTrue(html.contains(">GitHub</a> &middot; <a"));
        assertTrue(html.contains(">Patreon</a>"));
        assertFalse(html.contains("javascript"));
        assertEquals("", renderer.render("""
                [{"type":"links"}]
                """, VALUES), "no links, no row");
    }

    @Test
    @DisplayName("A list of links is never written into the text as a value")
    void collectionsAreNotSubstituted() {
        String html = renderer.render(
                """
                [{"type":"paragraph","text":"x {{ links }} y"}]
                """, with(MailBlockRenderer.LINKS, List.of(new MailLink("GitHub", "https://example.invalid/gh"))));

        assertTrue(html.contains("x {{ links }} y"));
    }

    @Test
    @DisplayName("The shipped default reads whole for a product with everything set")
    void shippedDefaultRendersFully() {
        String html = renderer.render(DefaultPurchaseMail.BLOCKS, MailBlockRenderer.sampleValues("Chatty"));

        assertTrue(html.contains("Thank you for purchasing Chatty!"));
        assertTrue(html.contains("LYNA-DEMO-KEY-0000"));
        assertTrue(html.contains("Join the Discord"));
        assertTrue(html.contains("Leave a review"));
        assertTrue(html.contains("share access with up to 2 more people"));
        assertTrue(html.contains(">GitHub</a>"));
    }

    @Test
    @DisplayName("The shipped default leaves out what a bare product has nothing for")
    void shippedDefaultRendersForABareProduct() {
        Map<String, Object> bare = new java.util.LinkedHashMap<>();
        bare.put("name", "Ada");
        bare.put("key", "KEY-1");
        bare.put("product", "Chatty");
        bare.put("productPage", "https://example.invalid/products/1");
        bare.put("shares", 0);

        String html = renderer.render(DefaultPurchaseMail.BLOCKS, bare);

        assertTrue(html.contains("Thank you for purchasing Chatty!"));
        assertTrue(html.contains("KEY-1"));
        assertTrue(html.contains("Chatty on our store"));
        assertFalse(html.contains("Discord"), "no invite, nothing about Discord");
        assertFalse(html.contains("review"), "nowhere to review, no ask");
        assertFalse(html.contains("Documentation"), "no wiki, no wiki link");
        assertFalse(html.contains("<img"), "no icon, no image");
        assertFalse(html.contains("&middot;"), "no links, no row");
    }
}
