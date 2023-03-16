package de.chojo.lyna.commands.products.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Edit implements SlashHandler {
    private final Guilds guilds;

    public Edit(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var id = event.getOption("product", OptionMapping::getAsInt);
        Optional<Product> optProduct = guilds.guild(event.getGuild()).products().byId(id);

        if (optProduct.isEmpty()) {
            event.reply("Unknown product").setEphemeral(true).queue();
            return;
        }

        Product product = optProduct.get();

        String name = event.getOption("name", OptionMapping::getAsString);
        if (name != null && !name.isBlank()) {
            product.name(name);
        }
        Role role = event.getOption("role", OptionMapping::getAsRole);
        if (role != null) {
            product.role(role);
        }
        String url = event.getOption("url", OptionMapping::getAsString);
        if (url != null) {
            product.url(url);
        }
        Boolean free = event.getOption("free", () -> null, OptionMapping::getAsBoolean);
        if (free != null) {
            product.free(free);
        }
        Boolean trial = event.getOption("trial", () -> null, OptionMapping::getAsBoolean);
        if (trial != null) {
            product.trial(trial);
        }

        event.reply("Product updated").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue())).queue();
        }
    }
}
