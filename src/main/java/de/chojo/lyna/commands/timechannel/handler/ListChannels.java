/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.timechannel.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

import java.util.stream.Collectors;

/**
 * Lists the channels of this guild that show the time, with their zone and name.
 */
public class ListChannels implements SlashHandler {
    private final TimeChannelRepository channels;

    public ListChannels(TimeChannelRepository channels) {
        this.channels = channels;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext ctx) {
        var inGuild = channels.inGuild(event.getGuild().getIdLong());
        String text = inGuild.isEmpty()
                ? "No channel shows the time."
                : inGuild.stream()
                        .map(channel -> "<#%d> · %s · `%s`"
                                .formatted(channel.channelId(), channel.zone().getId(), channel.template()))
                        .collect(Collectors.joining("\n"));
        event.reply(text).setEphemeral(true).queue();
    }
}
