package de.chojo.lyna.commands.downloads.handler.type;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

public class EditType implements SlashHandler {
    private final Guilds guilds;

    public EditType(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        String name = event.getOption("new_name", OptionMapping::getAsString);
        String description = event.getOption("new_description", OptionMapping::getAsString);

        var optType = guilds.guild(event.getGuild()).downloadTypes().byId(event.getOption("name", OptionMapping::getAsInt));

        if (optType.isEmpty()) {
            event.reply("Invalid type").setEphemeral(true).queue();
            return;
        }

        var type = optType.get();

        if (name != null) {
            type.name(name);
        }

        if (description != null) {
            type.description(description);
        }

        event.reply("Updated type").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("name")) {
            event.replyChoices(guilds.guild(event.getGuild()).downloadTypes().complete(focusedOption.getValue())).queue();
        }
    }
}
