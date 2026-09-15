package de.chojo.lyna.mail;

import de.chojo.lyna.data.dao.products.mailings.Mailing;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Builds the mails that carry a licence.
 *
 * <p>The body is whatever the product's own template says, put inside the shared layout so that
 * every mail the application sends looks like the others. The subject comes from the catalogue
 * rather than from a sentence built here.
 */
public final class MailCreator {
    private static final de.chojo.lyna.mail.blocks.MailBlockRenderer BLOCKS =
            new de.chojo.lyna.mail.blocks.MailBlockRenderer();

    /** What a per-product template may refer to. */
    private static final String[] PLACEHOLDERS = {"name", "key", "product", "downloadUrl"};

    private MailCreator() {
    }

    /**
     * The mail telling somebody their licence.
     *
     * @param mailing     the product's own template, whose text becomes the body
     * @param key         the licence key
     * @param name        who bought it, as they gave it
     * @param address     where to send it
     * @param downloadUrl where the product can be downloaded, or null when there is nowhere to point
     */
    public static Mail createLicenseMessage(MailTemplateRenderer renderer, Mailing mailing, String key,
                                            String name, String address, String downloadUrl) {
        Map<String, Object> values = new LinkedHashMap<>();
        values.put("name", name);
        values.put("key", key);
        values.put("product", mailing.product().name());
        values.put("downloadUrl", downloadUrl);

        Map<String, Object> context = new HashMap<>(values);
        context.put("body", body(mailing, values));

        return new Mail(address,
                renderer.subject("licence-issued", "en", values),
                renderer.render("licence-custom", "en", context));
    }

    /**
     * The body of the mail: what the operator composed, however they composed it.
     *
     * <p>A product whose mail was written before blocks existed still has only its HTML, and it is
     * carried across as it was. One composed since renders from its blocks, where the escaping is
     * decided per block rather than left to whoever typed it.
     */
    private static String body(Mailing mailing, Map<String, Object> values) {
        if (mailing.blocks() != null && !mailing.blocks().isBlank()) {
            return BLOCKS.render(mailing.blocks(), values);
        }
        return substitute(mailing.mailText(), values);
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
