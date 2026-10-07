/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.timechannel.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

/**
 * Stops a channel showing the time. Its name stays as it was last set.
 */
public class Remove implements SlashHandler {
    private final TimeChannelRepository channels;

    public Remove(TimeChannelRepository channels) {
        this.channels = channels;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext ctx) {
        GuildChannel channel = event.getOption("channel", OptionMapping::getAsChannel);
        boolean removed = channel != null && channels.remove(channel.getGuild().getIdLong(), channel.getIdLong());
        event.reply(removed ? "%s no longer shows the time.".formatted(channel.getAsMention()) : "That channel does not show the time.")
                .setEphemeral(true)
                .queue();
    }
}
