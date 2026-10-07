/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.timechannel;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.timechannel.handler.ListChannels;
import de.chojo.lyna.commands.timechannel.handler.Remove;
import de.chojo.lyna.commands.timechannel.handler.SetChannel;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import de.chojo.lyna.feature.timechannel.service.TimeChannelNames;
import de.chojo.lyna.feature.timechannel.service.TimeChannelSchedule;

/**
 * Channels whose name shows the time in a zone, renamed every quarter of an hour.
 */
public class TimeChannels implements SlashProvider<Slash> {
    private final TimeChannelRepository channels;
    private final TimeChannelSchedule schedule;

    @Inject
    public TimeChannels(TimeChannelRepository channels, TimeChannelSchedule schedule) {
        this.channels = channels;
        this.schedule = schedule;
    }

    @Override
    public Slash slash() {
        return Slash.of("timechannel", "Show the time in a zone as a channel name")
                .unlocalized()
                .adminCommand()
                .guildOnly()
                .subCommand(SubCommand.of("set", "Make a channel show the time in a zone")
                        .handler(new SetChannel(channels, schedule))
                        .argument(Argument.channel("channel", "A voice channel or category")
                                .asRequired())
                        .argument(Argument.text("zone", "Time zone, such as Europe/Berlin")
                                .asRequired()
                                .withAutoComplete())
                        .argument(Argument.text(
                                "template",
                                "The name, with %s where the time goes. Default: %s"
                                        .formatted(TimeChannelNames.TIME, TimeChannelNames.DEFAULT_TEMPLATE))))
                .subCommand(SubCommand.of("remove", "Stop a channel showing the time")
                        .handler(new Remove(channels))
                        .argument(Argument.channel("channel", "The channel").asRequired()))
                .subCommand(SubCommand.of("list", "List the channels showing the time")
                        .handler(new ListChannels(channels)))
                .build();
    }
}
