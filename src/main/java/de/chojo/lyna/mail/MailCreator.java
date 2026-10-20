/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.mail;

import de.chojo.lyna.mail.blocks.MailBlockRenderer;

import java.util.HashMap;
import java.util.Map;

/**
 * Builds the mails that carry a licence.
 *
 * <p>The body is whatever the product's own template says, put inside the shared layout so that
 * every mail the application sends looks like the others. The subject comes from the catalogue
 * rather than from a sentence built here.
 */
public final class MailCreator {
    private static final MailBlockRenderer BLOCKS = new MailBlockRenderer();

    /** What a per-product template may refer to. */
    private static final String[] PLACEHOLDERS = {
        "name",
        "key",
        "product",
        "downloadUrl",
        "productUrl",
        "productPage",
        "wikiUrl",
        "reviewUrl",
        "discordUrl",
        "shares"
    };

    private MailCreator() {}

    /**
     * The mail telling somebody their licence.
     *
     * @param blocks    the blocks the body is composed from, or null to send {@code html} instead
     * @param html      the HTML of a mail written before blocks existed, sent when there are no blocks
     * @param values    what the placeholders stand for, including {@code key} and {@code name}
     * @param address   where to send it
     * @param recipient what the application knows about who is receiving it
     */
    public static Mail createLicenseMessage(
            MailTemplateRenderer renderer,
            String blocks,
            String html,
            Map<String, Object> values,
            String address,
            PurchaseRecipient recipient) {
        Map<String, Object> context = new HashMap<>(values);
        context.put("body", body(blocks, html, values));
        context.put("tellAboutAccount", recipient != PurchaseRecipient.UNSTATED);
        context.put("hasAccount", recipient == PurchaseRecipient.WITH_ACCOUNT);
        context.put("buyerAddress", address);

        return new Mail(
                address,
                renderer.subject("licence-issued", "en", values),
                renderer.render("licence-custom", "en", context));
    }

    /**
     * The body of the mail: the blocks it is composed from, or the HTML a mail written before blocks
     * existed still carries, filled in as it always was.
     */
    private static String body(String blocks, String html, Map<String, Object> values) {
        if (blocks != null) return BLOCKS.render(blocks, values);
        return substitute(html == null ? "" : html, values);
    }

    /**
     * Fills a product's own template in.
     *
     * <p>By replacement rather than by rendering it as a template. The text is written by an
     * operator and stored in the database, and a template engine handed a string from there can
     * reach rather more than the values it was given.
     */
    private static String substitute(String text, Map<String, Object> values) {
        String result = text;
        for (String placeholder : PLACEHOLDERS) {
            Object value = values.get(placeholder);
            result = result.replace("{{ " + placeholder + " }}", value == null ? "" : value.toString());
            result = result.replace("{{" + placeholder + "}}", value == null ? "" : value.toString());
        }
        return result;
    }
}
