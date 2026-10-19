/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.service;

import java.util.regex.Pattern;

/**
 * The first few words of a product's description, as plain text, for a list that has room for a line
 * or two and no room for markdown.
 *
 * <p>The first paragraph that is prose: headings, images, badges and HTML are what a README opens
 * with, and none of them says what the plugin does.
 */
public final class DescriptionAbstract {
    /** How long an abstract is at most, in characters. */
    public static final int LIMIT = 200;

    private static final Pattern PARAGRAPHS = Pattern.compile("\\R\\s*\\R");
    private static final Pattern IMAGE = Pattern.compile("!\\[[^\\]]*]\\([^)]*\\)");
    private static final Pattern LINK = Pattern.compile("\\[([^\\]]*)]\\([^)]*\\)");
    private static final Pattern TAG = Pattern.compile("<[^>]+>");
    private static final Pattern MARKUP = Pattern.compile("[*_`~]+");
    private static final Pattern SPACE = Pattern.compile("\\s+");

    private DescriptionAbstract() {}

    /**
     * @param markdown the description as it was written, or null
     * @return its first prose paragraph as plain text, cut at a word to fit {@link #LIMIT}, or null
     *         when there is none
     */
    public static String of(String markdown) {
        if (markdown == null) return null;
        for (String paragraph : PARAGRAPHS.split(markdown)) {
            String text = plain(paragraph);
            if (!text.isEmpty()) return cut(text);
        }
        return null;
    }

    private static String plain(String paragraph) {
        String trimmed = paragraph.strip();
        if (trimmed.startsWith("#") || trimmed.startsWith(">") || trimmed.startsWith("```")) return "";
        String text = IMAGE.matcher(trimmed).replaceAll("");
        text = LINK.matcher(text).replaceAll("$1");
        text = TAG.matcher(text).replaceAll("");
        text = MARKUP.matcher(text).replaceAll("");
        return SPACE.matcher(text).replaceAll(" ").strip();
    }

    private static String cut(String text) {
        if (text.length() <= LIMIT) return text;
        int end = text.lastIndexOf(' ', LIMIT - 1);
        return text.substring(0, end > 0 ? end : LIMIT - 1).stripTrailing() + "…";
    }
}
