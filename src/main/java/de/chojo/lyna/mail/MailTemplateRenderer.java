/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.mail;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.pebbletemplates.pebble.PebbleEngine;
import io.pebbletemplates.pebble.loader.ClasspathLoader;
import io.pebbletemplates.pebble.template.PebbleTemplate;

import java.io.IOException;
import java.io.InputStream;
import java.io.StringWriter;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Renders the mails the application sends.
 *
 * <p>Every mail is a body template extending {@code _layout.html}, which carries the chrome and the
 * styling. A body says what this mail is about and nothing about how it looks, so all of them stay
 * in step when the layout changes.
 *
 * <p>Subjects come from {@code i18n/mail_<locale>.json} rather than from the call site. A subject
 * built by concatenating a sentence in Java is one nobody can translate and one that drifts from the
 * body it belongs to.
 *
 * <p>Templates are read from the classpath, not from a directory beside the application. Lyna ships
 * as one archive and an operator customises the per-product mail through the web rather than by
 * editing files on the host, so there is nothing to mount and nothing to keep in step with a
 * deployment.
 *
 * <p>Autoescaping is on. Anything that is HTML on purpose has to say so with {@code | raw}, which
 * makes each of those a decision somebody wrote down.
 *
 * <p>What the instance is called and where it lives are merged into every mail here rather than
 * passed by whoever sends one. A caller that has to remember them is a caller that can forget them,
 * and the licence mail did: it was the one mail going out signed with the default name and with no
 * link in its footer.
 */
public class MailTemplateRenderer {
    private static final String TEMPLATE_ROOT = "mail/";
    private static final String FALLBACK_LOCALE = "en";

    private final PebbleEngine engine;
    private final ObjectMapper json = new ObjectMapper();
    private final Map<String, Map<String, String>> subjects = new ConcurrentHashMap<>();
    private final Map<String, Object> instanceValues;

    /**
     * @param senderName what the instance signs its mail with
     * @param baseUrl    where the instance lives, for the footer and for links back into it
     */
    public MailTemplateRenderer(String senderName, String baseUrl) {
        this.instanceValues = Map.of("senderName", senderName, "baseUrl", baseUrl);
        ClasspathLoader loader = new ClasspathLoader(MailTemplateRenderer.class.getClassLoader());
        loader.setPrefix(TEMPLATE_ROOT);
        loader.setSuffix("");
        this.engine = new PebbleEngine.Builder()
                .loader(loader)
                .autoEscaping(true)
                .strictVariables(false)
                .cacheActive(true)
                .build();
    }

    /**
     * Renders one mail.
     *
     * @param name      the template, without a locale or an extension, e.g. {@code licence-issued}
     * @param locale    the locale to render in, falling back to English
     * @param variables what the template refers to
     * @return the rendered HTML
     */
    public String render(String name, String locale, Map<String, Object> variables) {
        String effective = resolveLocale(name, locale);
        PebbleTemplate template = engine.getTemplate(effective + "/" + name + ".html");
        Map<String, Object> context = new HashMap<>(variables);
        instanceValues.forEach(context::putIfAbsent);
        context.putIfAbsent("lang", effective);
        try (StringWriter writer = new StringWriter()) {
            template.evaluate(writer, context);
            return writer.toString();
        } catch (IOException e) {
            throw new IllegalStateException("Could not render mail template " + name, e);
        }
    }

    /**
     * The subject line for a mail, with its placeholders filled in.
     *
     * <p>A key nobody has written a subject for answers with the key itself: a mail with an odd
     * subject still arrives, where one that threw would not.
     *
     * @param name         the template the subject belongs to
     * @param locale       the locale, falling back to English
     * @param placeholders what {@code {name}} in the subject refers to
     * @return the subject line
     */
    public String subject(String name, String locale, Map<String, Object> placeholders) {
        String template = lookupSubject(locale, name);
        if (template == null) template = lookupSubject(FALLBACK_LOCALE, name);
        if (template == null) return name;
        Map<String, Object> values = new HashMap<>(placeholders);
        instanceValues.forEach(values::putIfAbsent);
        String result = template;
        for (Map.Entry<String, Object> entry : values.entrySet()) {
            if (entry.getValue() == null) continue;
            result = result.replace("{" + entry.getKey() + "}", entry.getValue().toString());
        }
        return result;
    }

    /**
     * @return the locale to render in: the one asked for when it has this template, English otherwise
     */
    private String resolveLocale(String name, String locale) {
        if (locale == null || locale.isBlank() || locale.equals(FALLBACK_LOCALE)) return FALLBACK_LOCALE;
        String path = TEMPLATE_ROOT + locale + "/" + name + ".html";
        return MailTemplateRenderer.class.getClassLoader().getResource(path) == null ? FALLBACK_LOCALE : locale;
    }

    private String lookupSubject(String locale, String key) {
        if (locale == null || locale.isBlank()) return null;
        return subjects.computeIfAbsent(locale, this::loadSubjects).get(key);
    }

    private Map<String, String> loadSubjects(String locale) {
        Map<String, String> loaded = new HashMap<>();
        try (InputStream in =
                MailTemplateRenderer.class.getClassLoader().getResourceAsStream("i18n/mail_" + locale + ".json")) {
            if (in == null) return loaded;
            JsonNode subject = json.readTree(in).path("subject");
            subject.fieldNames()
                    .forEachRemaining(
                            field -> loaded.put(field, subject.path(field).asText()));
        } catch (IOException e) {
            throw new IllegalStateException("Could not read the mail subjects for " + locale, e);
        }
        return loaded;
    }
}
