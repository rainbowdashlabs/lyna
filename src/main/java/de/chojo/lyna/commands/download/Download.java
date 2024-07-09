package de.chojo.lyna.commands.download;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.web.api.Api;
import de.chojo.lyna.commands.download.handler.Default;
import de.chojo.lyna.data.access.Guilds;

public class Download extends SlashCommand {
    public Download(Guilds guilds, Api api) {
        super(Slash.of("download", "Download releases.")
                .unlocalized()
                .command(new Default(guilds, api))
                .argument(Argument.text("product", "The product you want to download").asRequired().withAutoComplete()));
    }
}
