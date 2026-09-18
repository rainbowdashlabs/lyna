/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.license;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Group;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.license.handler.Create;
import de.chojo.lyna.commands.license.handler.delete.Identifier;
import de.chojo.lyna.commands.license.handler.delete.Key;
import de.chojo.lyna.commands.license.handler.downloads.Grant;
import de.chojo.lyna.commands.license.handler.downloads.Revoke;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.feature.license.service.LicenseService;

public class License implements SlashProvider<Slash> {
    private final LicenseService licenseService;
    private final Guilds guilds;
    private final Conf configuration;

    @Inject
    public License(Guilds guilds, Conf configuration, LicenseService licenseService) {
        this.licenseService = licenseService;
        this.guilds = guilds;
        this.configuration = configuration;
    }

    @Override
    public Slash slash() {
        return Slash.of("license", "Manage licenses")
                .unlocalized()
                .adminCommand()
                .guildOnly()
                .subCommand(SubCommand.of("create", "Create a new license")
                        .handler(new Create(guilds, licenseService))
                        .argument(Argument.text("product", "Product name")
                                .withAutoComplete()
                                .asRequired())
                        .argument(Argument.text("user_identifier", "Unique user identifier")
                                .asRequired()))
                .group(Group.of("delete", "Delete a license")
                        .subCommand(SubCommand.of("key", "Delete by key")
                                .handler(new Key(guilds, licenseService))
                                .argument(Argument.text("key", "Key to delete").asRequired()))
                        .subCommand(SubCommand.of("identifier", "Delete a license by identifier")
                                .handler(new Identifier(guilds, licenseService))
                                .argument(Argument.text("product", "Product name")
                                        .withAutoComplete()
                                        .asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier")
                                        .withAutoComplete()
                                        .asRequired())))
                .group(Group.of("downloads", "Manage license download rights")
                        .subCommand(SubCommand.of("grant", "Grant download rights to a license")
                                .handler(new Grant(guilds, licenseService))
                                .argument(Argument.text("product", "Product name")
                                        .withAutoComplete()
                                        .asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier")
                                        .withAutoComplete()
                                        .asRequired())
                                .argument(Argument.text("type", "Download type")
                                        .withAutoComplete()
                                        .asRequired()))
                        .subCommand(SubCommand.of("revoke", "Revoke download rights from a license")
                                .handler(new Revoke(guilds, licenseService))
                                .argument(Argument.text("product", "Product name")
                                        .withAutoComplete()
                                        .asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier")
                                        .withAutoComplete()
                                        .asRequired())
                                .argument(Argument.text("type", "Download type")
                                        .withAutoComplete()
                                        .asRequired())))
                .build();
    }
}
