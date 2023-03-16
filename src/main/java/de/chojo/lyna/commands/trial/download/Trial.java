package de.chojo.lyna.commands.trial.download;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.api.Api;
import de.chojo.lyna.commands.trial.download.handler.Default;
import de.chojo.lyna.data.access.Guilds;

public class Trial extends SlashCommand {
    public Trial(Guilds guilds, Api api) {
        super(Slash.of("trial", "Download a product once to test it.")
                .unlocalized()
                .command(new Default(guilds, api))
                .argument(Argument.text("product", "The product you want to download").asRequired().withAutoComplete()));
    }
}
