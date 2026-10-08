/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.download;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.commands.download.handler.Default;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.guild.Guilds;

public class Download extends SlashCommand {
    @Inject
    public Download(Guilds guilds, Api api) {
        super(Slash.of("download", "Download releases.")
                .unlocalized()
                .command(new Default(guilds, api))
                .argument(Argument.text("product", "The product you want to download")
                        .asRequired()
                        .withAutoComplete()));
    }
}
