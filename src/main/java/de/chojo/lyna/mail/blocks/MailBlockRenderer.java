/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.mail.blocks;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.feature.mail.entity.MailLink;

import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
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
 * <p><strong>Leaving out.</strong> A link may point at a placeholder, {@code [wiki]({{ wikiUrl }})},
 * and a value that is not an http address renders the label alone. A block whose every such link came
 * up empty is left out, as is a block naming in {@code requires} a placeholder that stands for
 * nothing, so one default mail reads right for a product that has a wiki and for one that does not.
 *
 * <p>The one exception is the {@code raw} block, which is HTML on purpose. It is how a template
 * written before this existed keeps working, and it is the only block whose content reaches the mail
 * unexamined.
 */
public class MailBlockRenderer {
    /** `[label](https://…)` or `[label]({{ placeholder }})`, the only ways an operator writes a link. */
    private static final Pattern LINK =
            Pattern.compile("\\[([^\\]]{1,200})]\\((https?://[^)\\s]{1,500}|\\{\\{ ?([A-Za-z]{1,40}) ?}})\\)");

    /** The value a {@code links} block reads, a list of {@link MailLink}. */
    public static final String LINKS = "links";

    private static final String TEXT_STYLE = "color:#555;font-size:15px;line-height:1.6;";

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
            if (missesRequired(block, values)) continue;
            out.append(renderBlock(block, values));
        }
        return out.toString();
    }

    /**
     * Whether the block names a placeholder it needs and that placeholder stands for nothing: empty,
     * zero or false.
     */
    private static boolean missesRequired(JsonNode block, Map<String, Object> values) {
        for (JsonNode name : block.path("requires")) {
            String value = scalar(values.get(name.asText(""))).strip();
            if (value.isEmpty() || value.equals("0") || value.equalsIgnoreCase("false")) return true;
        }
        return false;
    }

    private String renderBlock(JsonNode block, Map<String, Object> values) {
        String type = block.path("type").asText("");
        return switch (type) {
            case "heading" -> renderHeading(block, values);
            case "paragraph" -> renderParagraph(block, values);
            case "key" -> renderKey(block, values);
            case "button" -> renderButton(block, values);
            case "list" -> renderList(block, values);
            case "image" -> renderImage(block, values);
            case "links" -> renderLinks(values);
            case "divider" -> "<hr style=\"border:0;border-top:1px solid #eee;margin:24px 0;\">";
            case "raw" -> block.path("html").asText("");
            default -> "";
        };
    }

    private String renderHeading(JsonNode block, Map<String, Object> values) {
        Text text = text(block.path("text").asText(""), values);
        if (text.onlyEmptyLinks()) return "";
        return "<h2 style=\"color:#1a1a1a;margin:0 0 16px;font-size:19px;%s\">%s</h2>"
                .formatted(align(block), text.html());
    }

    /**
     * A paragraph, centred or in italics when the operator asked for it. Nothing richer: the layout
     * decides how a mail looks, and these two are what a thank-you line needs.
     */
    private String renderParagraph(JsonNode block, Map<String, Object> values) {
        Text text = text(block.path("text").asText(""), values);
        if (text.onlyEmptyLinks()) return "";
        String html = block.path("emphasis").asBoolean(false) ? "<em>" + text.html() + "</em>" : text.html();
        return "<p style=\"%s%s\">%s</p>".formatted(TEXT_STYLE, align(block), html);
    }

    private static String align(JsonNode block) {
        return "center".equals(block.path("align").asText("")) ? "text-align:center;" : "";
    }

    private String renderKey(JsonNode block, Map<String, Object> values) {
        String label =
                text(block.path("label").asText("Your licence key"), values).html();
        String key = escape(scalar(values.get("key")));
        return ("<p style=\"color:#888;font-size:12px;text-transform:uppercase;letter-spacing:.06em;"
                        + "margin:0 0 6px;text-align:center;\">%s</p>"
                        + "<code style=\"display:block;background:#f5f5f5;border:1px solid #e0e0e0;border-radius:6px;"
                        + "padding:14px 16px;margin:20px 0;font-family:'Courier New',Courier,monospace;font-size:16px;"
                        + "color:#1a1a1a;word-break:break-all;text-align:center;\">%s</code>")
                .formatted(label, key);
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
        String label = text(block.path("label").asText("Download"), values).html();
        String url = substitutePlain(block.path("url").asText(""), values).trim();
        if (!isWeb(url)) return "";
        return ("<div style=\"text-align:center;margin:24px 0;\">"
                        + "<a style=\"display:inline-block;background:#E92063;color:#fff;padding:12px 32px;border-radius:6px;"
                        + "text-decoration:none;font-weight:bold;font-size:15px;\" href=\"%s\">%s</a></div>")
                .formatted(escape(url), label);
    }

    /**
     * A list, without the items whose links all came up empty, and nothing at all when none is left.
     */
    private String renderList(JsonNode block, Map<String, Object> values) {
        List<String> items = new ArrayList<>();
        for (JsonNode item : block.path("items")) {
            Text text = text(item.asText(""), values);
            if (!text.onlyEmptyLinks()) items.add(text.html());
        }
        if (items.isEmpty()) return "";
        StringBuilder out = new StringBuilder("<ul style=\"" + TEXT_STYLE + "padding-left:20px;\">");
        for (String item : items) {
            out.append("<li>").append(item).append("</li>");
        }
        return out.append("</ul>").toString();
    }

    /**
     * An image, centred and never wider than the mail. Checked like a button's address, since it is
     * the other field that becomes an attribute.
     */
    private String renderImage(JsonNode block, Map<String, Object> values) {
        String url = substitutePlain(block.path("url").asText(""), values).trim();
        if (!isWeb(url)) return "";
        String alt = escape(substitutePlain(block.path("alt").asText(""), values));
        return ("<div style=\"text-align:center;margin:0 0 24px;\"><img src=\"%s\" alt=\"%s\" "
                        + "style=\"max-width:100%%;max-height:128px;height:auto;border:0;\"></div>")
                .formatted(escape(url), alt);
    }

    /**
     * The guild's links as one centred row, or nothing when it has none.
     */
    private static String renderLinks(Map<String, Object> values) {
        if (!(values.get(LINKS) instanceof Collection<?> links)) return "";
        List<String> anchors = new ArrayList<>();
        for (Object entry : links) {
            if (entry instanceof MailLink(String label, String url) && url != null && isWeb(url.trim())) {
                anchors.add(anchor(escape(url.trim()), escape(label == null ? "" : label)));
            }
        }
        if (anchors.isEmpty()) return "";
        return "<p style=\"%stext-align:center;font-weight:bold;\">%s</p>"
                .formatted(TEXT_STYLE, String.join(" &middot; ", anchors));
    }

    /**
     * One piece of operator-written text, ready for the mail.
     *
     * <p>Escaped, then linked, then filled in - in that order. Linking before the values go in is
     * what stops a product named {@code [x](javascript:…)} from becoming a link. A link to a
     * placeholder is resolved while linking, and only to an http address.
     */
    private Text text(String raw, Map<String, Object> values) {
        Matcher matcher = LINK.matcher(escape(raw));
        StringBuilder out = new StringBuilder();
        int placeholderLinks = 0;
        int emptyLinks = 0;
        while (matcher.find()) {
            String label = matcher.group(1);
            String replacement;
            if (matcher.group(3) == null) {
                replacement = anchor(matcher.group(2), label);
            } else {
                placeholderLinks++;
                String url = scalar(values.get(matcher.group(3))).trim();
                if (isWeb(url)) {
                    replacement = anchor(escape(url), label);
                } else {
                    emptyLinks++;
                    replacement = label;
                }
            }
            matcher.appendReplacement(out, Matcher.quoteReplacement(replacement));
        }
        matcher.appendTail(out);
        return new Text(substituteEscaped(out.toString(), values), placeholderLinks, emptyLinks);
    }

    /**
     * @param html             the text, ready for the mail
     * @param placeholderLinks how many of its links pointed at a placeholder
     * @param emptyLinks       how many of those stood for nothing
     */
    private record Text(String html, int placeholderLinks, int emptyLinks) {
        boolean onlyEmptyLinks() {
            return placeholderLinks > 0 && placeholderLinks == emptyLinks;
        }
    }

    private static String anchor(String href, String label) {
        return "<a style=\"color:#E92063;\" href=\"%s\">%s</a>".formatted(href, label);
    }

    private static boolean isWeb(String url) {
        return url.startsWith("http://") || url.startsWith("https://");
    }

    private static String scalar(Object value) {
        return value == null || value instanceof Collection<?> ? "" : value.toString();
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
            if (entry.getValue() instanceof Collection<?>) continue;
            String value = scalar(entry.getValue());
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
        values.put("productUrl", "https://example.invalid/source");
        values.put("productPage", "https://example.invalid/products/1");
        values.put("productIcon", "https://example.invalid/icon.png");
        values.put("wikiUrl", "https://example.invalid/wiki");
        values.put("reviewUrl", "https://example.invalid/review");
        values.put("discordUrl", "https://example.invalid/discord");
        values.put("shares", 2);
        values.put(LINKS, List.of(new MailLink("GitHub", "https://example.invalid/github")));
        return values;
    }
}
