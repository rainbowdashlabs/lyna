package de.chojo.lyna.commands.registrations;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Group;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.registrations.handler.Info;
import de.chojo.lyna.commands.registrations.handler.List;
import de.chojo.lyna.commands.registrations.handler.Transfer;
import de.chojo.lyna.commands.registrations.handler.share.Add;
import de.chojo.lyna.commands.registrations.handler.share.Remove;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.services.RoleService;

public class Registrations implements SlashProvider<Slash> {
    private final Guilds guilds;
    private final RoleService roleService;

    public Registrations(Guilds guilds, RoleService roleService) {
        this.guilds = guilds;
        this.roleService = roleService;
    }

    @Override
    public Slash slash() {
        return Slash.of("registrations", "Manage your registered products")
                .unlocalized()
                .group(Group.of("share", "Share your registrations")
                        .subCommand(SubCommand.of("add", "Add a user to your license")
                                .handler(new Add(guilds, roleService))
                                .argument(Argument.text("product", "The product to share").asRequired().withAutoComplete())
                                .argument(Argument.user("user", "User to share the license with.").asRequired()))
                        .subCommand(SubCommand.of("remove", "Remove user from a license")
                                .handler(new Remove(guilds, roleService))
                                .argument(Argument.text("product", "Product name").asRequired().withAutoComplete())
                                .argument(Argument.user("user", "User to remove sharing"))
                                .argument(Argument.text("user_id", "User id to remove sharing"))))
                .subCommand(SubCommand.of("info", "Information about a license")
                        .handler(new Info(guilds))
                        .argument(Argument.text("product", "The product name").asRequired().withAutoComplete()))
                .subCommand(SubCommand.of("list", "List your licenses")
                        .handler(new List(guilds)))
                .subCommand(SubCommand.of("transfer", "Transfer a license to another user.")
                        .handler(new Transfer(guilds, roleService))
                        .argument(Argument.text("product", "The product to transfer").asRequired().withAutoComplete())
                        .argument(Argument.user("user", "User to transfer the license to.").asRequired()))
                .build();
    }
}
