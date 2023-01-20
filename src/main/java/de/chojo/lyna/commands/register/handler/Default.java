package de.chojo.lyna.commands.register.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.License;
import de.chojo.lyna.services.RoleService;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Default implements SlashHandler {
    private final Guilds guilds;
    private final RoleService roleService;

    public Default(Guilds guilds, RoleService roleService) {
        this.guilds = guilds;
        this.roleService = roleService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        Optional<License> license = guilds.guild(event.getGuild()).licenses()
                                          .byKey(event.getOption("key", OptionMapping::getAsString));
        if (license.isEmpty()) {
            event.reply("Invalid key").setEphemeral(true).queue();
            return;
        }

        if (license.get().isClaimed()) {
            event.reply("This license is already claimed").setEphemeral(true).queue();
            return;
        }

        license.get().claim(event.getMember());
        event.reply("License claimed. Roles have been assigned.").setEphemeral(true).queue();
        roleService.claim(license.get());
    }
}
