/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.register.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.service.LicenseService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Default implements SlashHandler {
    private final LicenseService licenseService;
    private final Guilds guilds;

    public Default(Guilds guilds, LicenseService licenseService) {
        this.guilds = guilds;
        this.licenseService = licenseService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        Optional<License> license = guild.licenses().byKey(event.getOption("key", OptionMapping::getAsString));
        if (license.isEmpty()) {
            event.reply("Invalid key").setEphemeral(true).queue();
            return;
        }

        if (licenseService.isClaimed(license.get())) {
            event.reply("This license is already claimed").setEphemeral(true).queue();
            return;
        }

        LicenseUser user = guild.user(event.getMember());
        if (user.licenseByProduct(license.get().product()).isPresent()) {
            event.reply("The user already owns a license for this product.")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        licenseService.claim(license.get(), event.getMember());
        event.reply("License claimed. Roles have been assigned.")
                .setEphemeral(true)
                .queue();
    }
}
