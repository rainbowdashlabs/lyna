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
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.product.service.ProductRoleService;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;

public class Download extends SlashCommand {
    @Inject
    public Download(Guilds guilds, Proxy proxy, ProductRoleService productRoles) {
        super(Slash.of("download", "Download releases.")
                .unlocalized()
                .command(new Default(guilds, proxy, productRoles))
                .argument(Argument.text("product", "The product you want to download")
                        .asRequired()
                        .withAutoComplete()));
    }
}
