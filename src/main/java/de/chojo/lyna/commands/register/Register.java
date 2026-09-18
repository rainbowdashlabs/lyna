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

public class Register implements SlashProvider<Slash> {
    private final Guilds guilds;

    @Inject
    public Register(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public Slash slash() {
        return Slash.of("register", "Register a product key")
                .unlocalized()
                .guildOnly()
                .command(new Default(guilds))
                .argument(Argument.text("key", "The product key").asRequired())
                .build();
    }
}
