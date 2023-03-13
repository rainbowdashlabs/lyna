package de.chojo.lyna.commands.downloads.handler.download;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Completion;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.nexus.NexusRest;
import de.chojo.nexus.entities.ComponentXO;
import de.chojo.nexus.entities.RepositoryXO;
import de.chojo.nexus.requests.v1.search.Direction;
import de.chojo.nexus.requests.v1.search.Sort;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;
import java.util.stream.Collectors;

public class CreateDownload implements SlashHandler {
    private final Guilds guilds;
    private final NexusRest nexusRest;

    public CreateDownload(Guilds guilds, NexusRest nexusRest) {
        this.guilds = guilds;
        this.nexusRest = nexusRest;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        Optional<Product> product = guild.products().byId(event.getOption("product").getAsInt());
        Optional<DownloadType> type = guild.downloadTypes().byId(event.getOption("type").getAsInt());
        String repository = event.getOption("repository", OptionMapping::getAsString);
        String groupId = event.getOption("group_id", OptionMapping::getAsString);
        String artifactId = event.getOption("artifact_id", OptionMapping::getAsString);
        String classifier = event.getOption("classifier", OptionMapping::getAsString);

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        if (type.isEmpty()) {
            event.reply("Invalid type").setEphemeral(true).queue();
            return;
        }

        product.get().downloads().create(type.get(), repository, groupId, artifactId, classifier);
        event.reply("Download created.").setEphemeral(true).queue();
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
        if (focusedOption.getName().equals("repository")) {
            event.replyChoices(Completion.complete(focusedOption.getValue(),
                            nexusRest.v1()
                                    .repositories()
                                    .list()
                                    .complete()
                                    .stream()
                                    .map(RepositoryXO::name)))
                    .queue();
        }
        if (focusedOption.getName().equals("group_id")) {
            event.replyChoices(Completion.complete(focusedOption.getValue(),
                            nexusRest.v1()
                                    .search()
                                    .search()
                                    .mavenGroupId(focusedOption.getValue() + "*")
                                    .sort(Sort.VERSION)
                                    .direction(Direction.ASC)
                                    .complete()
                                    .stream()
                                    .map(ComponentXO::group)
                                    .collect(Collectors.toSet())))
                    .queue();
        }
        if (focusedOption.getName().equals("artifact_id")) {
            event.replyChoices(Completion.complete(focusedOption.getValue(),
                            nexusRest.v1()
                                    .search()
                                    .search()
                                    .mavenArtifactId(focusedOption.getValue() + "*")
                                    .sort(Sort.VERSION)
                                    .direction(Direction.ASC)
                                    .complete()
                                    .stream()
                                    .map(ComponentXO::name)
                                    .collect(Collectors.toSet())))
                    .queue();
        }

    }
}
