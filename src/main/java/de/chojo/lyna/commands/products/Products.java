package de.chojo.lyna.commands.products;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.products.handler.Create;
import de.chojo.lyna.commands.products.handler.Delete;
import de.chojo.lyna.commands.products.handler.Edit;
import de.chojo.lyna.data.access.Guilds;

public class Products implements SlashProvider<Slash> {
    private final Guilds guilds;

    public Products(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public Slash slash() {
        return Slash.of("products", "Manage products")
                .unlocalized()
                .adminCommand()
                .guildOnly()
                .subCommand(SubCommand.of("create", "Create a new product")
                        .handler(new Create(guilds))
                        .argument(Argument.text("name", "Product name").asRequired())
                        .argument(Argument.role("role", "Role of product").asRequired())
                        .argument(Argument.text("url", "Url to product"))
                        .argument(Argument.bool("free", "Mark product as free"))
                        .argument(Argument.bool("trial", "Allow trial download"))
                )
                .subCommand(SubCommand.of("edit", "Edit a product")
                        .handler(new Edit(guilds))
                        .argument(Argument.text("product", "Product").asRequired().withAutoComplete())
                        .argument(Argument.text("name", "Product name"))
                        .argument(Argument.role("role", "Role of product"))
                        .argument(Argument.text("url", "Url to product"))
                        .argument(Argument.bool("free", "Mark product as free"))
                        .argument(Argument.bool("trial", "Allow trial download"))
                )
                .subCommand(SubCommand.of("delete", "Delete a product and everything connected to it")
                        .handler(new Delete(guilds))
                        .argument(Argument.text("name", "Product name").asRequired().withAutoComplete())
                        .argument(Argument.bool("confirm", "Confirm deletion").asRequired())
                )
                .build();
    }
}
