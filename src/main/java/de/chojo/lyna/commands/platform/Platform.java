package de.chojo.lyna.commands.platform;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.platform.handler.Create;
import de.chojo.lyna.commands.platform.handler.Delete;
import de.chojo.lyna.data.access.Guilds;

public class Platform implements SlashProvider<Slash> {
    private final Guilds guilds;

    public Platform(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public Slash slash() {
        return Slash.of("platform", "Manage platforms")
                .unlocalized()
                .subCommand(SubCommand.of("create", "Create a new platform")
                        .handler(new Create(guilds))
                        .argument(Argument.text("name", "Platform name").asRequired())
                        .argument(Argument.text("url", "Url to platform"))
                )
                .subCommand(SubCommand.of("delete", "Delete a platform and everything connected to it")
                        .handler(new Delete(guilds))
                        .argument(Argument.text("name", "Platform name").asRequired().withAutoComplete())
                        .argument(Argument.bool("confirm", "Confirm deletion").asRequired())
                )
                .build();
    }
}
