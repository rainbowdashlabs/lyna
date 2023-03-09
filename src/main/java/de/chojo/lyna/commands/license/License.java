package de.chojo.lyna.commands.license;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Group;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.license.handler.Create;
import de.chojo.lyna.commands.license.handler.delete.Identifier;
import de.chojo.lyna.commands.license.handler.delete.Key;
import de.chojo.lyna.commands.license.handler.downloads.Grant;
import de.chojo.lyna.commands.license.handler.downloads.Revoke;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.Guilds;

public class License implements SlashProvider<Slash> {
    private final Guilds guilds;
    private final Configuration<ConfigFile> configuration;

    public License(Guilds guilds, Configuration<ConfigFile> configuration) {
        this.guilds = guilds;
        this.configuration = configuration;
    }

    @Override
    public Slash slash() {
        return Slash.of("license", "Manage licenses")
                .unlocalized()
                .adminCommand()
                .guildOnly()
                .subCommand(SubCommand.of("create", "Create a new license")
                        .handler(new Create(guilds, configuration))
                        .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                        .argument(Argument.text("platform", "Platform of key").withAutoComplete().asRequired())
                        .argument(Argument.text("user_identifier", "Unique user identifier").asRequired()))
                .group(Group.of("delete", "Delete a license")
                        .subCommand(SubCommand.of("key", "Delete by key")
                                .handler(new Key(guilds))
                                .argument(Argument.text("key", "Key to delete").asRequired()))
                        .subCommand(SubCommand.of("identifier", "Delete a license by identifier")
                                .handler(new Identifier(guilds))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("platform", "Platform of key").withAutoComplete().asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier").withAutoComplete().asRequired())
                        )
                )
                .group(Group.of("downloads", "Manage license download rights")
                        .subCommand(SubCommand.of("grant", "Grant download rights to a license")
                                .handler(new Grant(guilds))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("platform", "Platform of key").withAutoComplete().asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier").withAutoComplete().asRequired())
                                .argument(Argument.text("type", "Download type").withAutoComplete().asRequired())
                        )
                        .subCommand(SubCommand.of("revoke", "Revoke download rights from a license")
                                .handler(new Revoke(guilds))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("platform", "Platform of key").withAutoComplete().asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier").withAutoComplete().asRequired())
                                .argument(Argument.text("type", "Download type").withAutoComplete().asRequired())
                        )
                )
                .build();
    }
}
