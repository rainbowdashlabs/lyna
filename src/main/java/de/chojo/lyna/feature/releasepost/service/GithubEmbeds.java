/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasepost.service;

import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.MessageEmbed;

import java.time.Instant;

/**
 * What every post about a repository's goings-on has in common: the product it belongs to above the
 * title, the repository below, and text cut to what Discord takes.
 */
public final class GithubEmbeds {
    /** How much of a pull request's or issue's description is shown. */
    public static final int BODY_LIMIT = 500;

    public static final int PUSH = 0x5865F2;
    public static final int OPENED = 0x2EA043;
    public static final int MERGED = 0x8957E5;
    public static final int CLOSED = 0x6E7681;

    private GithubEmbeds() {}

    /**
     * @param repository the repository's full name, or null when the payload did not say
     */
    public static EmbedBuilder base(String product, String title, String url, int color, String repository) {
        EmbedBuilder embed = new EmbedBuilder()
                .setAuthor(cut(product, MessageEmbed.AUTHOR_MAX_LENGTH))
                .setTitle(cut(title, MessageEmbed.TITLE_MAX_LENGTH), url)
                .setColor(color)
                .setTimestamp(Instant.now());
        if (repository != null) embed.setFooter(repository);
        return embed;
    }

    /**
     * @return the text, ending in an ellipsis when it had to be shortened to fit the limit
     */
    public static String cut(String text, int limit) {
        if (text == null) return "";
        String stripped = text.strip();
        return stripped.length() > limit ? stripped.substring(0, limit - 1) + "…" : stripped;
    }

    /**
     * @return the first line of a commit message, which is its subject
     */
    public static String firstLine(String text) {
        if (text == null) return "";
        int end = text.indexOf('\n');
        return (end < 0 ? text : text.substring(0, end)).strip();
    }
}
