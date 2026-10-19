/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.entity;

import java.util.List;

/**
 * What a guild's purchase mails share.
 *
 * @param guildId    the guild
 * @param blocks     the default mail as a block document, or null for the one the application ships
 * @param discordUrl where buyers join the guild's Discord, or null when there is nowhere to send them
 * @param links      the links the mail closes with, in order
 */
public record MailGuildSettings(long guildId, String blocks, String discordUrl, List<MailLink> links) {
    /**
     * @return the settings of a guild that has never saved any
     */
    public static MailGuildSettings empty(long guildId) {
        return new MailGuildSettings(guildId, null, null, List.of());
    }
}
