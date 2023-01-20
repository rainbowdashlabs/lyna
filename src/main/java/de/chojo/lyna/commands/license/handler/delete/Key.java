package de.chojo.lyna.commands.license.handler.delete;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.License;
import de.chojo.lyna.services.RoleService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Key implements SlashHandler {
    private final Guilds guilds;

    public Key(Guilds guilds, RoleService roleService) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        Optional<License> license = guilds.guild(event.getGuild())
                                          .licenses().byKey(event.getOption("key", OptionMapping::getAsString));

        if (license.isEmpty()) {
            event.reply("Invalid key").setEphemeral(true).queue();
            return;
        }

        if (license.get().delete()) {
            event.reply("Deleted").setEphemeral(true).queue();
        }
    }
}
