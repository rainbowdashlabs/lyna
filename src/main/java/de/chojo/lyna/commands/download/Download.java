package de.chojo.lyna.commands.download;

import com.google.inject.Inject;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;
import de.chojo.lyna.commands.download.handler.Default;
import de.chojo.lyna.data.access.Guilds;

public class Download extends SlashCommand {
    @Inject
    public Download(Guilds guilds, Proxy proxy) {
        super(Slash.of("download", "Download releases.")
                .unlocalized()
                .command(new Default(guilds, proxy))
                .argument(Argument.text("product", "The product you want to download").asRequired().withAutoComplete()));
    }
}
