package de.chojo.lyna.commands.settings;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Group;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;
import de.chojo.lyna.commands.settings.license.Shares;
import de.chojo.lyna.data.access.Guilds;

public class Settings implements SlashProvider<Slash> {
    private final Guilds guilds;

    public Settings(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public Slash slash() {
        ;
        return Slash.of("settings", "Manage guild settings")
                .unlocalized()
                .guildOnly()
                .group(Group.of("license", "Manage license settings")
                        .subCommand(SubCommand.of("shares", "Define how often a license can be shared")
                                .handler(new Shares(guilds))
                                .argument(Argument.integer("shares", "Number of shares").min(0).max(100))))
                .build();
    }
}
