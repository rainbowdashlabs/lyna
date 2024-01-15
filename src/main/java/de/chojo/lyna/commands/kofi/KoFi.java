package de.chojo.lyna.commands.kofi;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.kofi.handler.Link;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.KoFiProducts;

public class KoFi implements SlashProvider<Slash> {
    private final Guilds guilds;

    private final KoFiProducts products;

    public KoFi(Guilds guilds, KoFiProducts products) {
        this.guilds = guilds;
        this.products = products;
    }


    @Override
    public Slash slash() {
        return Slash.of("kofi", "Manage kofi products")
                .unlocalized()
                .adminCommand()
                .guildOnly()
                .subCommand(SubCommand.of("link", "link a link to a produce")
                        .handler(new Link(products, guilds))
                        .argument(Argument.text("link", "kofi link code").asRequired())
                        .argument(Argument.text("product", "product you want to link").asRequired().withAutoComplete()))
                .build();
    }
}
