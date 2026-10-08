/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.timechannel.repository;

import com.google.inject.Singleton;

import java.time.ZoneId;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The channels whose name shows the time somewhere.
 */
@Singleton
public class TimeChannelRepository {

    /**
     * @param template the name, with {@code {time}} where the time goes
     */
    public record TimeChannel(long guildId, long channelId, ZoneId zone, String template) {}

    public void set(TimeChannel channel) {
        query("""
                INSERT INTO time_channel (channel_id, guild_id, zone, template) VALUES (?, ?, ?, ?)
                ON CONFLICT (channel_id) DO UPDATE SET zone = excluded.zone, template = excluded.template
                """)
                .single(call().bind(channel.channelId())
                        .bind(channel.guildId())
                        .bind(channel.zone().getId())
                        .bind(channel.template()))
                .insert();
    }

    /**
     * @return whether the channel showed a time
     */
    public boolean remove(long guildId, long channelId) {
        return query("DELETE FROM time_channel WHERE guild_id = ? AND channel_id = ?")
                .single(call().bind(guildId).bind(channelId))
                .delete()
                .changed();
    }

    public List<TimeChannel> inGuild(long guildId) {
        return query(
                        "SELECT guild_id, channel_id, zone, template FROM time_channel WHERE guild_id = ? ORDER BY channel_id")
                .single(call().bind(guildId))
                .map(row -> new TimeChannel(
                        row.getLong("guild_id"),
                        row.getLong("channel_id"),
                        ZoneId.of(row.getString("zone")),
                        row.getString("template")))
                .all();
    }

    public List<TimeChannel> all() {
        return query("SELECT guild_id, channel_id, zone, template FROM time_channel")
                .single()
                .map(row -> new TimeChannel(
                        row.getLong("guild_id"),
                        row.getLong("channel_id"),
                        ZoneId.of(row.getString("zone")),
                        row.getString("template")))
                .all();
    }
}
