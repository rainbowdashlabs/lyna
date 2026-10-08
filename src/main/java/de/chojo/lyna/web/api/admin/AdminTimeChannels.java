/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.admin;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository.TimeChannel;
import de.chojo.lyna.feature.timechannel.service.TimeChannelNames;
import de.chojo.lyna.feature.timechannel.service.TimeChannelSchedule;
import de.chojo.lyna.gateway.Gateway;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.delete;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Channels whose name shows a clock, as {@code /timechannel} manages them. Renaming needs a bot, so
 * without one nothing can be set up; the list still answers.
 */
public class AdminTimeChannels {
    private static final Set<ChannelType> RENAMEABLE =
            Set.of(ChannelType.VOICE, ChannelType.STAGE, ChannelType.CATEGORY);

    private final GuildAdminGuard guard;
    private final TimeChannelRepository channels;
    private final TimeChannelSchedule schedule;
    private final Gateway gateway;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param channelId   as text, since a Discord id is larger than a JavaScript number holds exactly
     * @param channelName the channel's current name, when a gateway knows it
     * @param shows       what the name says right now, by the template
     */
    public record TimeChannelView(String channelId, String channelName, String zone, String template, String shows) {}

    public record TimeChannelSet(String channelId, String zone, String template) {}

    public record ChannelView(String id, String name, String type) {}

    @Inject
    public AdminTimeChannels(
            GuildAdminGuard guard, TimeChannelRepository channels, TimeChannelSchedule schedule, Gateway gateway) {
        this.guard = guard;
        this.channels = channels;
        this.schedule = schedule;
        this.gateway = gateway;
    }

    /**
     * Mounts the routes under the guild being administered.
     */
    public void init() {
        get("time-channels", this::list);
        post("time-channels", this::set);
        delete("time-channels/{channelId}", this::remove);
        get("time-channels/channels", this::renameable);
        get("time-channels/zones", this::zones);
    }

    private void list(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        Optional<Guild> guild = gateway.guild(admin.guild().guildId());
        Instant now = Instant.now();
        ctx.json(channels.inGuild(admin.guild().guildId()).stream()
                .map(channel -> new TimeChannelView(
                        Long.toString(channel.channelId()),
                        guild.map(g -> g.getGuildChannelById(channel.channelId()))
                                .map(GuildChannel::getName)
                                .orElse(null),
                        channel.zone().getId(),
                        channel.template(),
                        TimeChannelNames.name(channel.template(), channel.zone(), now)))
                .toList());
    }

    /**
     * Makes a channel show a clock and renames it at once. Refused without a bot, for a channel the
     * bot cannot see, for a text channel - Discord would rewrite its name into lowercase words joined
     * by hyphens - for an unknown zone, and for a template without {@code {time}}.
     */
    private void set(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        TimeChannelSet body;
        long channelId;
        try {
            body = json.readValue(ctx.body(), TimeChannelSet.class);
            channelId = Long.parseLong(body.channelId().strip());
        } catch (Exception e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Choose a channel and a time zone");
            return;
        }
        Optional<Guild> guild = gateway.guild(admin.guild().guildId());
        if (guild.isEmpty()) {
            ctx.status(HttpStatus.CONFLICT).result("Time channels need the bot, and none is connected");
            return;
        }
        GuildChannel channel = guild.get().getGuildChannelById(channelId);
        if (channel == null || !RENAMEABLE.contains(channel.getType())) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Use a voice channel or a category of this guild");
            return;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(body.zone() == null ? "" : body.zone().strip());
        } catch (DateTimeException e) {
            ctx.status(HttpStatus.BAD_REQUEST).result("That is not a time zone");
            return;
        }
        String template = body.template() == null || body.template().isBlank()
                ? TimeChannelNames.DEFAULT_TEMPLATE
                : body.template().strip();
        if (!template.contains(TimeChannelNames.TIME)) {
            ctx.status(HttpStatus.BAD_REQUEST)
                    .result("The name needs %s where the time goes".formatted(TimeChannelNames.TIME));
            return;
        }
        TimeChannel timeChannel = new TimeChannel(admin.guild().guildId(), channelId, zone, template);
        channels.set(timeChannel);
        schedule.refresh(timeChannel);
        ctx.status(HttpStatus.NO_CONTENT);
    }

    private void remove(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        long channelId;
        try {
            channelId = Long.parseLong(ctx.pathParam("channelId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        ctx.status(channels.remove(admin.guild().guildId(), channelId) ? HttpStatus.NO_CONTENT : HttpStatus.NOT_FOUND);
    }

    /**
     * The channels a clock can be put on. Empty without a bot.
     */
    private void renameable(Context ctx) {
        var admin = guard.require(ctx);
        if (admin == null) return;
        ctx.json(gateway.guild(admin.guild().guildId())
                .map(guild -> guild.getChannels().stream()
                        .filter(channel -> RENAMEABLE.contains(channel.getType()))
                        .map(channel -> new ChannelView(
                                channel.getId(),
                                channel.getName(),
                                channel.getType().name()))
                        .toList())
                .orElse(List.of()));
    }

    private void zones(Context ctx) {
        if (guard.require(ctx) == null) return;
        ctx.json(TimeChannelNames.zonesMatching(
                ctx.queryParamAsClass("q", String.class).getOrDefault(""), 25));
    }
}
