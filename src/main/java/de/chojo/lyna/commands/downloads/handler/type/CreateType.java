package de.chojo.lyna.commands.downloads.handler.type;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Choice;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Arrays;

public class CreateType implements SlashHandler {
    private final Guilds guilds;

    public CreateType(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        String name = event.getOption("name", OptionMapping::getAsString);
        String description = event.getOption("description", OptionMapping::getAsString);
        ReleaseType releaseType = ReleaseType.valueOf(event.getOption("release_type", OptionMapping::getAsString));

        var newType = guilds.guild(event.getGuild()).downloadTypes().create(name, description, releaseType);
        if (newType.isPresent()) {
            event.reply("New type created").setEphemeral(true).queue();
            return;
        }
        event.reply("Could not create new type. Maybe it already exists.").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("release_type")) {
            event.replyChoices(Arrays.stream(ReleaseType.values()).map(Enum::name).map(Choice::toChoice).toList()).queue();
        }
    }
}
