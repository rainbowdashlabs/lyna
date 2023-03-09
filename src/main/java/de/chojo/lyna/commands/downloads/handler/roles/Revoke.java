package de.chojo.lyna.commands.downloads.handler.roles;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Collections;

public class Revoke implements SlashHandler {

        private final Guilds guilds;

    public Revoke(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var optProduct = guilds.guild(event.getGuild()).products().byId(event.getOption("product", OptionMapping::getAsInt));
        if (optProduct.isEmpty()) {
            event.reply("Invalid Product").setEphemeral(true).queue();
            return;
        }

        Role role = event.getOption("role", OptionMapping::getAsRole);
        ReleaseType releaseType = ReleaseType.parse(event.getOption("type", OptionMapping::getAsString));

        optProduct.get().downloads().revoke(role, releaseType);
        event.reply("Revoked downloads of type %s from %s".formatted(releaseType.name(), role.getAsMention()))
                .mention(Collections.emptyList())
                .setEphemeral(true)
                .queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue())).queue();
        }
        if (focusedOption.getName().equals("type")) {
            event.replyChoices(Completion.complete(focusedOption.getValue(), ReleaseType.class)).queue();
        }

    }

}
