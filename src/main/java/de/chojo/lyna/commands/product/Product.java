package de.chojo.lyna.commands.product;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.product.handler.Delete;
import de.chojo.lyna.commands.product.handler.Create;
import de.chojo.lyna.data.access.Guilds;

public class Product implements SlashProvider<Slash> {
    private final Guilds guilds;

    public Product(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public Slash slash() {
        return Slash.of("products", "Manage products")
                .unlocalized()
                .subCommand(SubCommand.of("create", "Create a new product")
                        .handler(new Create(guilds))
                        .argument(Argument.text("name", "Product name").asRequired())
                        .argument(Argument.role("role", "Role of product").asRequired())
                        .argument(Argument.text("url", "Url to product"))
                )
                .subCommand(SubCommand.of("delete", "Delete a product and everything connected to it")
                        .handler(new Delete(guilds))
                        .argument(Argument.text("name", "Product name").asRequired().withAutoComplete())
                        .argument(Argument.bool("confirm", "Confirm deletion").asRequired())
                )
                .build();
    }
}
