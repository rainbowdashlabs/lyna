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

public class EditDownload implements SlashHandler {

    private final Guilds guilds;

    public EditDownload(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        Optional<Product> optProduct = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        Optional<DownloadType> optType = guild.downloadTypes().byId(event.getOption("type", OptionMapping::getAsInt));

        if (optProduct.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        if (optType.isEmpty()) {
            event.reply("Invalid type").setEphemeral(true).queue();
            return;
        }

        Optional<Download> optDownload = optProduct.get().downloads().byType(optType.get());
        if (optDownload.isEmpty()) {
            event.reply("Download type not defined for this product").setEphemeral(true).queue();
            return;
        }

        Download download = optDownload.get();

        String repository = event.getOption("repository", OptionMapping::getAsString);
        String groupId = event.getOption("group_id", OptionMapping::getAsString);
        String artifactId = event.getOption("artifact_id", OptionMapping::getAsString);
        String classifier = event.getOption("classifier", OptionMapping::getAsString);

        if (repository != null) {
            download.repository(repository);
        }

        if (groupId != null) {
            download.groupId(groupId);
        }

        if (artifactId != null) {
            download.artifactId(artifactId);
        }

        if (classifier != null) {
            if (classifier.isBlank()) {
                download.artifactId(null);
            } else {
                download.artifactId(artifactId);
            }
        }

        event.reply("Updated").setEphemeral(true).queue();
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
