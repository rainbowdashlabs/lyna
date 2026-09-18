package de.chojo.lyna.service;

import de.chojo.lyna.mail.blocks.MailBlockRenderer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

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
}
