/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.license.handler.delete;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.service.LicenseService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Key implements SlashHandler {
    private final LicenseService licenseService;
    private final Guilds guilds;

    public Key(Guilds guilds, LicenseService licenseService) {
        this.guilds = guilds;
        this.licenseService = licenseService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        Optional<License> license =
                guilds.guild(event.getGuild()).licenses().byKey(event.getOption("key", OptionMapping::getAsString));

        if (license.isEmpty()) {
            event.reply("Invalid key").setEphemeral(true).queue();
            return;
        }

        if (licenseService.delete(license.get())) {
            event.reply("Deleted").setEphemeral(true).queue();
        }
    }
}
