package de.chojo.lyna.commands.product.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Role;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Create implements SlashHandler {
    private final Guilds guilds;

    public Create(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        String name = event.getOption("name", OptionMapping::getAsString);
        Role role = event.getOption("role", OptionMapping::getAsRole);
        String url = event.getOption("url", () -> null, OptionMapping::getAsString);

        Optional<Product> product = guilds.guild(event.getGuild()).products()
                .create(name, role, url);

        if(product.isEmpty()){
            event.reply("Product name is taken").setEphemeral(true).queue();
            return;
        }

        event.reply("Product created").setEphemeral(true).queue();
    }
}
