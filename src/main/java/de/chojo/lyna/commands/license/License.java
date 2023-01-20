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
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.services.RoleService;

public class License implements SlashProvider<Slash> {
    private final Guilds guilds;
    private final Configuration<ConfigFile> configuration;
    private final RoleService roleService;

    public License(Guilds guilds, Configuration<ConfigFile> configuration, RoleService roleService) {
        this.guilds = guilds;
        this.configuration = configuration;
        this.roleService = roleService;
    }

    @Override
    public Slash slash() {
        return Slash.of("license", "Manage licenses")
                .unlocalized()
                .guildOnly()
                .subCommand(SubCommand.of("create", "Create a new license")
                        .handler(new Create(guilds, configuration))
                        .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                        .argument(Argument.text("platform", "Platform of key").withAutoComplete().asRequired())
                        .argument(Argument.text("user_identifier", "Unique user identifier").asRequired()))
                .group(Group.of("delete", "Delete a license")
                        .subCommand(SubCommand.of("key", "Delte by key")
                                .handler(new Key(guilds, roleService))
                                .argument(Argument.text("key", "Key to delete").asRequired()))
                        .subCommand(SubCommand.of("identifier", "Delte a license by identifier")
                                .handler(new Identifier(guilds, roleService))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("platform", "Platform of key").withAutoComplete().asRequired())
                                .argument(Argument.text("user_identifier", "Unique user identifier").withAutoComplete().asRequired())))
                .build();
    }
}
