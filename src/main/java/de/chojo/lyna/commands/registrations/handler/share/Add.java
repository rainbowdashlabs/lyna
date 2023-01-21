package de.chojo.lyna.commands.registrations.handler.share;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.services.RoleService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;

public class Add implements SlashHandler {
    private final Guilds guilds;

    public Add(Guilds guilds, RoleService roleService) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {

    }
}
