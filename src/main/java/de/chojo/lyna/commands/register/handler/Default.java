package de.chojo.lyna.commands.register.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.licenses.License;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Default implements SlashHandler {
    private final Guilds guilds;

    public Default(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        Optional<License> license = guild.licenses().byKey(event.getOption("key", OptionMapping::getAsString));
        if (license.isEmpty()) {
            event.reply("Invalid key").setEphemeral(true).queue();
            return;
        }

        if (license.get().isClaimed()) {
            event.reply("This license is already claimed").setEphemeral(true).queue();
            return;
        }

        LicenseUser user = guild.user(event.getMember());
        if (user.licenseByProduct(license.get().product()).isPresent()) {
            event.reply("The user already owns a license for this product.").setEphemeral(true).queue();
            return;
        }

        license.get().claim(event.getMember());
        event.reply("License claimed. Roles have been assigned.").setEphemeral(true).queue();
    }
}
