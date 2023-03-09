package de.chojo.lyna.commands.downloads.handler.type;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class DeleteType implements SlashHandler {
    private final Guilds guilds;

    public DeleteType(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var type = guilds.guild(event.getGuild()).downloadTypes().byId(event.getOption("name", OptionMapping::getAsInt));

        if (type.isEmpty()) {
            event.reply("Invalid type").setEphemeral(true).queue();
            return;
        }

        if (type.get().delete()) {
            event.reply("Deleted type").setEphemeral(true).queue();
            return;
        }
        event.reply("Could not delete type").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("name")) {
            event.replyChoices(guilds.guild(event.getGuild()).downloadTypes().complete(focusedOption.getValue())).queue();
        }
    }
}
