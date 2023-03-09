package de.chojo.lyna.commands.license.handler.downloads;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Revoke implements SlashHandler {
    private final Guilds guilds;

    public Revoke(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var platform = guild.platforms().byId(event.getOption("platform", OptionMapping::getAsInt));
        var userIdentifier = event.getOption("user_identifier", OptionMapping::getAsString);

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        if (platform.isEmpty()) {
            event.reply("Invalid platform").setEphemeral(true).queue();
            return;
        }

        Optional<License> license = guild.licenses().byDetails(product.get(), platform.get(), userIdentifier);

        if (license.isEmpty()) {
            event.reply("Invalid license").queue();
            return;
        }

        license.get().grantAccess(ReleaseType.parse(event.getOption("type", OptionMapping::getAsString)));
        event.reply("Revokes download access from license").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue())).queue();
        }
        if (focusedOption.getName().equals("platform")) {
            event.replyChoices(guilds.guild(event.getGuild()).platforms().complete(focusedOption.getValue())).queue();
        }
        if (focusedOption.getName().equals("user_identifier")) {
            event.replyChoices(guilds.guild(event.getGuild()).licenses().completeIdentifier(focusedOption.getValue()))
                    .queue();
        }
        if (focusedOption.getName().equals("type")) {
            event.replyChoices(Completion.complete(focusedOption.getValue(), ReleaseType.class)).queue();
        }
    }
}
