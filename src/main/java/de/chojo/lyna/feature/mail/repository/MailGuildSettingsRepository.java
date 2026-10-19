/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.mail.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.mail.entity.MailGuildSettings;
import de.chojo.lyna.feature.mail.entity.MailLink;
import de.chojo.lyna.feature.mail.entity.MailLinks;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The guild-wide half of the purchase mail: the default every product without its own body sends,
 * and the links it fills in.
 */
@Singleton
public class MailGuildSettingsRepository {
    /**
     * @return what the guild saved, or {@link MailGuildSettings#empty} when it never saved anything
     */
    public MailGuildSettings get(long guildId) {
        return query("SELECT blocks, discord_url, links FROM mail_guild_settings WHERE guild_id = ?")
                .single(call().bind(guildId))
                .map(row -> new MailGuildSettings(
                        guildId,
                        row.getString("blocks"),
                        row.getString("discord_url"),
                        MailLinks.read(row.getString("links"))))
                .first()
                .orElseGet(() -> MailGuildSettings.empty(guildId));
    }

    /**
     * @param blocks the default mail as a block document, or null to go back to the shipped one
     */
    public void blocks(long guildId, String blocks) {
        query("""
                INSERT INTO mail_guild_settings (guild_id, blocks) VALUES (?, ?::JSONB)
                ON CONFLICT (guild_id) DO UPDATE SET blocks = excluded.blocks
                """).single(call().bind(guildId).bind(blocks)).insert();
    }

    /**
     * @param discordUrl where buyers join the guild's Discord, or null for nowhere
     * @param links      the links the mail closes with
     */
    public void contact(long guildId, String discordUrl, List<MailLink> links) {
        query("""
                INSERT INTO mail_guild_settings (guild_id, discord_url, links) VALUES (?, ?, ?::JSONB)
                ON CONFLICT (guild_id) DO UPDATE SET discord_url = excluded.discord_url, links = excluded.links
                """)
                .single(call().bind(guildId).bind(discordUrl).bind(MailLinks.write(links)))
                .insert();
    }
}
