/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.settings.license;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.entity.LicenseSettings;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class Shares implements SlashHandler {
    private final Guilds guilds;

    public Shares(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        OptionMapping shares = event.getOption("shares");
        LicenseSettings license = guilds.guild(event.getGuild()).settings().license();
        if (shares != null) {
            license.shares(shares.getAsInt());
        }

        event.reply("Maximum license shares are set to %d.".formatted(license.shares()))
                .setEphemeral(true)
                .queue();
    }
}
