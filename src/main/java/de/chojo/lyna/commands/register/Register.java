package de.chojo.lyna.commands.register;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.register.handler.Default;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.services.RoleService;

public class Register implements SlashProvider<Slash> {
    private final Guilds guilds;
    private final RoleService roleService;

    public Register(Guilds guilds, RoleService roleService) {
        this.guilds = guilds;
        this.roleService = roleService;
    }

    @Override
    public Slash slash() {
        return Slash.of("register", "Register a product key")
                .unlocalized()
                .command(new Default(guilds, roleService))
                .argument(Argument.text("key", "The product key").asRequired())
                .build();
    }
}
