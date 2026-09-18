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
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.product.service.TrialService;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;

public class Trial extends SlashCommand {
    @Inject
    public Trial(Guilds guilds, Proxy proxy, TrialService trials) {
        super(Slash.of("trial", "Download a product once to test it.")
                .unlocalized()
                .command(new Default(guilds, proxy, trials))
                .argument(Argument.text("product", "The product you want to download")
                        .asRequired()
                        .withAutoComplete()));
    }
}
