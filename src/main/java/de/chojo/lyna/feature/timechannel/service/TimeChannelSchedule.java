/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.timechannel.service;

import com.google.inject.Inject;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository;
import de.chojo.lyna.feature.timechannel.repository.TimeChannelRepository.TimeChannel;
import de.chojo.lyna.gateway.Gateway;
import net.dv8tion.jda.api.entities.channel.middleman.GuildChannel;
import org.slf4j.Logger;

import java.time.Instant;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Renames every time channel on each quarter of an hour.
 *
 * <p>Does nothing without a gateway: there is nothing to rename through. A channel the bot cannot
 * see is skipped rather than forgotten, since the bot may simply not have it cached yet.
 */
public class TimeChannelSchedule {
    private static final Logger log = getLogger(TimeChannelSchedule.class);

    private final Threading threading;
    private final Gateway gateway;
    private final TimeChannelRepository channels;

    @Inject
    public TimeChannelSchedule(Threading threading, Gateway gateway, TimeChannelRepository channels) {
        this.threading = threading;
        this.gateway = gateway;
        this.channels = channels;
    }

    public void start() {
        if (!gateway.connected()) return;
        threading.botWorker().scheduleAtFixedRate(
                this::refreshAll,
                TimeChannelNames.untilNextRename(Instant.now()).toMillis(),
                TimeChannelNames.INTERVAL.toMillis(),
                TimeUnit.MILLISECONDS);
    }

    /**
     * Renames one channel now, as when it was just set up.
     */
    public void refresh(TimeChannel channel) {
        Optional<GuildChannel> found = gateway.guild(channel.guildId())
                .map(guild -> guild.getGuildChannelById(channel.channelId()));
        if (found.isEmpty()) {
            log.debug("Time channel {} is not visible to the bot", channel.channelId());
            return;
        }
        String name = TimeChannelNames.name(channel.template(), channel.zone(), Instant.now());
        if (name.equals(found.get().getName())) return;
        found.get().getManager().setName(name).queue(
                success -> {},
                error -> log.warn("Could not rename time channel {}", channel.channelId(), error));
    }

    private void refreshAll() {
        try {
            channels.all().forEach(this::refresh);
        } catch (Exception e) {
            log.error("Could not refresh time channels", e);
        }
    }
}
