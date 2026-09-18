/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.register;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.register.handler.Default;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.feature.license.service.LicenseService;

public class Register implements SlashProvider<Slash> {
    private final LicenseService licenseService;
    private final Guilds guilds;

    @Inject
    public Register(Guilds guilds, LicenseService licenseService) {
        this.licenseService = licenseService;
        this.guilds = guilds;
    }

    @Override
    public Slash slash() {
        return Slash.of("register", "Register a product key")
                .unlocalized()
                .guildOnly()
                .command(new Default(guilds, licenseService))
                .argument(Argument.text("key", "The product key").asRequired())
                .build();
    }
}
