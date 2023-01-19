package de.chojo.lyna.commands.product.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.Command;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;
import java.util.Optional;

public class Delete implements SlashHandler {
    private final Guilds guilds;

    public Delete(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var id = event.getOption("name", OptionMapping::getAsInt);
        Optional<Product> product = guilds.guild(event.getGuild()).products().byId(id);

        if(product.isEmpty()){
            event.reply("Unknown product").setEphemeral(true).queue();
            return;
        }

        if (product.get().delete()) {
            event.reply("Deleted").setEphemeral(true).queue();
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("name")) {
            List<Product> products = guilds.guild(event.getGuild()).products().all();
            var value = focusedOption.getValue().toLowerCase();
            List<Command.Choice> choices = products.stream().filter(p -> p.name().toLowerCase().startsWith(value) || value.isBlank())
                                                   .map(p -> new Command.Choice(p.name(), p.id()))
                                                   .limit(25)
                                                   .toList();
            event.replyChoices(choices).queue();
        }
    }
}
