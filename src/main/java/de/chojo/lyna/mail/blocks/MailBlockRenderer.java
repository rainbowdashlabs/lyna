package de.chojo.lyna.mail.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Turns a per-product mail, as its operator composed it, into the HTML that goes in the layout.
 *
 * <p>The mail is stored as blocks rather than as markup, and the markup is decided here. A mail
 * client is not a browser: it wants inline styles and a narrow set of elements, and what a rich-text
 * editor produces is neither. Composing from blocks means an operator cannot write markup that does
 * not render, because they never write markup at all.
 *
 * <p><strong>Escaping.</strong> Everything an operator typed is escaped first. Links are then read
 * out of the escaped text, so only the operator can make one, and only to somewhere http. Values
 * like a name or a licence key are substituted last and escaped as they go, so neither the text nor
 * the values can carry markup into the mail.
 *
 * <p>The one exception is the {@code raw} block, which is HTML on purpose. It is how a template
 * written before this existed keeps working, and it is the only block whose content reaches the mail
 * unexamined.
 */
public class MailBlockRenderer {
    /** `[label](https://…)`, the only way an operator writes a link. */
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]{1,200})]\\((https?://[^)\\s]{1,500})\\)");

    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param blocks what the operator composed, as the JSON stored against the product
     * @param values what the placeholders in it refer to
     * @return the HTML body, for the layout to wrap
     */
    public String render(String blocks, Map<String, Object> values) {
        if (blocks == null || blocks.isBlank()) return "";
        JsonNode parsed;
        try {
            parsed = json.readTree(blocks);
        } catch (Exception e) {
            throw new IllegalArgumentException("The stored mail is not a block document", e);
        }
        if (!parsed.isArray()) throw new IllegalArgumentException("The stored mail is not a list of blocks");

        StringBuilder out = new StringBuilder();
        for (JsonNode block : parsed) {
            out.append(renderBlock(block, values));
        }
        return out.toString();
    }

    private String renderBlock(JsonNode block, Map<String, Object> values) {
        String type = block.path("type").asText("");
        return switch (type) {
            case "heading" -> "<h2 style=\"color:#1a1a1a;margin:0 0 16px;font-size:19px;\">%s</h2>"
                    .formatted(text(block.path("text").asText(""), values));
            case "paragraph" -> "<p style=\"color:#555;font-size:15px;line-height:1.6;\">%s</p>"
                    .formatted(text(block.path("text").asText(""), values));
            case "key" -> renderKey(block, values);
            case "button" -> renderButton(block, values);
            case "list" -> renderList(block, values);
            case "divider" -> "<hr style=\"border:0;border-top:1px solid #eee;margin:24px 0;\">";
            case "raw" -> block.path("html").asText("");
            default -> "";
        };
    }

    private String renderKey(JsonNode block, Map<String, Object> values) {
        String label = text(block.path("label").asText("Your licence key"), values);
        String key = escape(String.valueOf(values.getOrDefault("key", "")));
        return ("<p style=\"color:#888;font-size:12px;text-transform:uppercase;letter-spacing:.06em;"
                + "margin:0 0 6px;text-align:center;\">%s</p>"
                + "<code style=\"display:block;background:#f5f5f5;border:1px solid #e0e0e0;border-radius:6px;"
                + "padding:14px 16px;margin:20px 0;font-family:'Courier New',Courier,monospace;font-size:16px;"
                + "color:#1a1a1a;word-break:break-all;text-align:center;\">%s</code>").formatted(label, key);
    }

    /**
     * A button, unless there is nowhere for it to go.
     *
     * <p>A URL is the one field an operator fills in that becomes an attribute, so it is checked
     * rather than escaped: a link is either somewhere http or it is not rendered. That also covers
     * the case of a placeholder like {@code downloadUrl} standing for nothing, where the alternative
     * is a button leading to an empty address.
     */
    private String renderButton(JsonNode block, Map<String, Object> values) {
        String label = text(block.path("label").asText("Download"), values);
        String url = substitutePlain(block.path("url").asText(""), values).trim();
        if (!url.startsWith("http://") && !url.startsWith("https://")) return "";
        return ("<div style=\"text-align:center;margin:24px 0;\">"
                + "<a style=\"display:inline-block;background:#E92063;color:#fff;padding:12px 32px;border-radius:6px;"
                + "text-decoration:none;font-weight:bold;font-size:15px;\" href=\"%s\">%s</a></div>")
                .formatted(escape(url), label);
    }

    private String renderList(JsonNode block, Map<String, Object> values) {
        JsonNode items = block.path("items");
        if (!items.isArray() || items.isEmpty()) return "";
        StringBuilder out = new StringBuilder("<ul style=\"color:#555;font-size:15px;line-height:1.6;padding-left:20px;\">");
        for (JsonNode item : items) {
            out.append("<li>").append(text(item.asText(""), values)).append("</li>");
        }
        return out.append("</ul>").toString();
    }

    /**
     * One piece of operator-written text, ready for the mail.
     *
     * <p>Escaped, then linked, then filled in - in that order. Linking before the values go in is
     * what stops a product named {@code [x](javascript:…)} from becoming a link.
     */
    private String text(String raw, Map<String, Object> values) {
        String escaped = escape(raw);
        String linked = link(escaped);
        return substituteEscaped(linked, values);
    }

    private static String link(String escaped) {
        Matcher matcher = LINK.matcher(escaped);
        StringBuilder out = new StringBuilder();
        while (matcher.find()) {
            String replacement = "<a style=\"color:#E92063;\" href=\"%s\">%s</a>"
                    .formatted(matcher.group(2), matcher.group(1));
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        return matcher.appendTail(out).toString();
    }

    private static String substituteEscaped(String text, Map<String, Object> values) {
        return substitute(text, values, true);
    }

    private static String substitutePlain(String text, Map<String, Object> values) {
        return substitute(text, values, false);
    }

    private static String substitute(String text, Map<String, Object> values, boolean escapeValues) {
        String result = text;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            String value = entry.getValue() == null ? "" : entry.getValue().toString();
            if (escapeValues) value = escape(value);
            result = result.replace("{{ " + entry.getKey() + " }}", value);
            result = result.replace("{{" + entry.getKey() + "}}", value);
        }
        return result;
    }

    private static String escape(String raw) {
        return raw.replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;")
                .replace("'", "&#39;");
    }

    /**
     * What a preview shows when nothing real is to hand.
     *
     * @return stand-in values for every placeholder a template may name
     */
    public static Map<String, Object> sampleValues(String productName) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", "Ada Lovelace");
        values.put("key", "LYNA-DEMO-KEY-0000");
        values.put("product", productName);
        values.put("downloadUrl", "https://example.invalid/download");
        return values;
    }
}
