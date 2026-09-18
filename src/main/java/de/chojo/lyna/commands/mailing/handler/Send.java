/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.mailing.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.guild.LicenseGuild;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.mail.entity.Mailing;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import de.chojo.lyna.mail.MailingService;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Send implements SlashHandler {
    private final LicenseService licenseService;
    private final MailingService mailingService;
    private final Conf configuration;
    private final Guilds guilds;
    private final PurchaseService purchases;

    public Send(
            MailingService mailingService,
            Conf configuration,
            Guilds guilds,
            PurchaseService purchases,
            LicenseService licenseService) {
        this.mailingService = mailingService;
        this.configuration = configuration;
        this.guilds = guilds;
        this.purchases = purchases;
        this.licenseService = licenseService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var address = event.getOption("address", OptionMapping::getAsString);
        var name = event.getOption("name", OptionMapping::getAsString);

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        Optional<Mailing> optMailing = product.get().mailings().get();
        if (optMailing.isEmpty()) {
            event.reply("No mailing found for this product").queue();
            return;
        }

        Optional<License> license = product.get().createLicense(address);

        if (license.isEmpty()) {
            event.reply("A license does already exist for this address")
                    .setEphemeral(true)
                    .queue();
            return;
        }

        licenseService.grantAccess(license.get(), ReleaseType.STABLE);

        purchases.announce(optMailing.get(), license.get(), name, address);
        event.reply("Email sent").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue(), false))
                    .queue();
        }
    }
}
