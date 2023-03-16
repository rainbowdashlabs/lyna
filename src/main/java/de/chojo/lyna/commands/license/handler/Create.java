package de.chojo.lyna.commands.license.handler;

import de.chojo.jdautil.configuratino.Configuration;
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
    private final Configuration<ConfigFile> configuration;

    public Create(Guilds guilds, Configuration<ConfigFile> configuration) {
        this.guilds = guilds;
        this.configuration = configuration;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());

        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var platform = guild.platforms().byId(event.getOption("platform", OptionMapping::getAsInt));
        var identifier = event.getOption("user_identifier", OptionMapping::getAsString);

        if (platform.isEmpty()) {
            event.reply("Invalid platform").setEphemeral(true).queue();
            return;
        }

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        Optional<License> license = guild.licenses()
                .create(configuration.config().license().baseSeed(), product.get(), platform.get(), identifier);

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
        if (focusedOption.getName().equals("platform")) {
            event.replyChoices(guilds.guild(event.getGuild()).platforms().complete(focusedOption.getValue())).queue();
        }
    }
}
