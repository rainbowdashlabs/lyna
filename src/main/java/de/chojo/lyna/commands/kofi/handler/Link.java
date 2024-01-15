package de.chojo.lyna.commands.kofi.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Link implements SlashHandler {
    private final KoFiProducts koFiProducts;
    private final Guilds guilds;

    public Link(KoFiProducts koFiProducts, Guilds guilds) {
        this.koFiProducts = koFiProducts;
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext ctx) {
        Optional<Product> product;
        try {
            product = guilds.guild(event.getGuild()).products().byId(event.getOption("product", OptionMapping::getAsInt));
        } catch (NumberFormatException e) {
            event.reply("Invalid product.").setEphemeral(true).queue();
            return;
        }
        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }
        koFiProducts.create(product.get(), event.getOption("link", OptionMapping::getAsString));
        event.reply("Linked").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue())).queue();
        }
    }
}
