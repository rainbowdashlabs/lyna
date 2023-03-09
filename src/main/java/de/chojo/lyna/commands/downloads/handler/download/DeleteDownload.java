package de.chojo.lyna.commands.downloads.handler.download;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class DeleteDownload implements SlashHandler {
    private final Guilds guilds;

    public DeleteDownload(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        Optional<Product> product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        Optional<DownloadType> type = guild.downloadTypes().byId(event.getOption("type", OptionMapping::getAsInt));

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        if (type.isEmpty()) {
            event.reply("Invalid type").setEphemeral(true).queue();
            return;
        }

        Optional<Download> download = product.get().downloads().byType(type.get());
        if (download.isEmpty()) {
            event.reply("Download type not defined for this product").setEphemeral(true).queue();
            return;
        }

        if (download.get().delete()) {
            event.reply("Download deleted").setEphemeral(true).queue();
        } else {
            event.reply("Could not delete download").setEphemeral(true).queue();
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue())).queue();
        }
        if (focusedOption.getName().equals("type")) {
            event.replyChoices(guilds.guild(event.getGuild()).downloadTypes().complete(focusedOption.getValue())).queue();
        }
    }
}
