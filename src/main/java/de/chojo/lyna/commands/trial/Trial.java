/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.trial;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.commands.trial.handler.Default;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.guild.Guilds;

public class Trial extends SlashCommand {
    @Inject
    public Trial(Guilds guilds, Api api) {
        super(Slash.of("trial", "Download a product once to test it.")
                .unlocalized()
                .command(new Default(guilds, api))
                .argument(Argument.text("product", "The product you want to download")
                        .asRequired()
                        .withAutoComplete()));
    }
}
