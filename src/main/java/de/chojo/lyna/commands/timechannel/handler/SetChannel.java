/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.timechannel.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository.TimeChannel;
import de.chojo.lyna.feature.timechannel.service.TimeChannelNames;
import de.chojo.lyna.feature.timechannel.service.TimeChannelSchedule;
import net.dv8tion.jda.api.entities.channel.ChannelType;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.time.DateTimeException;
import java.time.Instant;
import java.time.ZoneId;
import java.util.Objects;

/**
 * Makes a channel show the time in a zone, and renames it straight away.
 *
 * <p>A text channel is refused: Discord turns its name into lowercase words joined by hyphens, so
 * {@code Developer Time: 14:15} would arrive as {@code developer-time-1415}.
 */
public class SetChannel implements SlashHandler {
    private final TimeChannelRepository channels;
    private final TimeChannelSchedule schedule;

    public SetChannel(TimeChannelRepository channels, TimeChannelSchedule schedule) {
        this.channels = channels;
        this.schedule = schedule;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext ctx) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);
        if (channel == null || channel.getType() == ChannelType.TEXT || channel.getType() == ChannelType.NEWS) {
            event.reply("Use a voice channel or a category. Text channel names cannot hold spaces or colons.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        ZoneId zone;
        try {
            zone = ZoneId.of(Objects.requireNonNull(event.getOption("zone", OptionMapping::getAsString))
                    .strip());
        } catch (DateTimeException e) {
            event.reply("That is not a time zone. Try one the list offers, such as Europe/Berlin.")
                    .setEphemeral(true)
                    .queue();
            return;
        }
        String template = event.getOption("template", TimeChannelNames.DEFAULT_TEMPLATE, OptionMapping::getAsString);
        if (!template.contains(TimeChannelNames.TIME)) {
            event.reply("The name needs %s where the time goes.".formatted(TimeChannelNames.TIME))
                    .setEphemeral(true)
                    .queue();
            return;
        }
        TimeChannel timeChannel = new TimeChannel(channel.getGuild().getIdLong(), channel.getIdLong(), zone, template);
        channels.set(timeChannel);
        schedule.refresh(timeChannel);
        event.reply("%s now shows the time in %s: **%s**"
                        .formatted(
                                channel.getAsMention(),
                                zone.getId(),
                                TimeChannelNames.name(template, zone, Instant.now())))
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        if (!event.getFocusedOption().getName().equals("zone")) return;
        event.replyChoices(
                        TimeChannelNames.zonesMatching(event.getFocusedOption().getValue(), 25).stream()
                                .map(zone -> new Command.Choice(zone, zone))
                                .toList())
                .queue();
    }
}
