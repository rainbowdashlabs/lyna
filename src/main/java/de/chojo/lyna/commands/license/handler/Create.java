package de.chojo.lyna.commands.license.handler;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Create implements SlashHandler {
    private final Guilds guilds;

    public Create(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());

        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var identifier = event.getOption("user_identifier", OptionMapping::getAsString);

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }


        Optional<License> license = product.get().createLicense(identifier);

        if (license.isEmpty()) {
            event.reply("License already created.").setEphemeral(true).queue();
            return;
        }

        license.get().grantAccess(ReleaseType.STABLE);

        event.reply("License created.\n`%s`".formatted(license.get().key())).setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue(), false)).queue();
        }
    }
}
