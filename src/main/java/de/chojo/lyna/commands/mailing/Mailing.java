/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.mailing;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.commands.mailing.handler.Create;
import de.chojo.lyna.commands.mailing.handler.Edit;
import de.chojo.lyna.commands.mailing.handler.Send;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import de.chojo.lyna.mail.MailingService;

public class Mailing extends SlashCommand {
    @Inject
    public Mailing(
            Guilds guilds,
            Conf configuration,
            MailingService mailingService,
            PurchaseService purchases,
            LicenseService licenseService) {
        super(Slash.of("mailing", "Configure mailing")
                .unlocalized()
                .adminCommand()
                .subCommand(SubCommand.of("create", "Create a new mailing setting")
                        .handler(new Create(guilds))
                        .argument(
                                Argument.text("product", "product").asRequired().withAutoComplete())
                        .argument(Argument.text("mail_name", "Name of product in the mail")
                                .asRequired())
                        .argument(Argument.attachment("mail", "A file containing the mail text")))
                .subCommand(SubCommand.of("send", "Send a mail to a person")
                        .handler(new Send(mailingService, configuration, guilds, purchases, licenseService))
                        .argument(
                                Argument.text("product", "product").asRequired().withAutoComplete())
                        .argument(Argument.text("address", "The receiver of the mail")
                                .asRequired())
                        .argument(Argument.text("name", "name of the receiver").asRequired()))
                .subCommand(SubCommand.of("edit", "Edit a mailing setting")
                        .handler(new Edit(guilds))
                        .argument(
                                Argument.text("product", "product").asRequired().withAutoComplete())
                        .argument(Argument.text("mail_name", "Name of product in the mail"))
                        .argument(Argument.attachment("mail", "A file containing the mail text"))));
    }
}
