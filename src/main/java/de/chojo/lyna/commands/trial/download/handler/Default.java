package de.chojo.lyna.commands.trial.download.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.menus.MenuAction;
import de.chojo.jdautil.menus.entries.MenuEntry;
import de.chojo.jdautil.util.Colors;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.api.Api;
import de.chojo.lyna.api.v1.download.proxy.AssetDownload;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.lyna.data.dao.settings.Trial;
import de.chojo.lyna.util.Formatting;
import de.chojo.nexus.entities.AssetXO;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;

import static de.chojo.lyna.util.Formatting.humanReadableByteCountSI;

public class Default implements SlashHandler {
    private final Guilds guilds;
    private final Api api;

    public Default(Guilds guilds, Api api) {
        this.guilds = guilds;
        this.api = api;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        Optional<Product> optProduct = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        if (optProduct.isEmpty()) {
            event.reply("Invalid Product").setEphemeral(true).queue();
            return;
        }

        Trial trial = guild.settings().trial();
        if (Duration.between(event.getMember().getTimeJoined(), OffsetDateTime.now()).toSeconds() > trial.serverTime().toSeconds()) {
            event.reply("You need to be part of the server for at least %s.".formatted(Formatting.duration(trial.serverTime())))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        if (Duration.between(event.getMember().getUser().getTimeCreated(), OffsetDateTime.now()).toSeconds() > trial.accountTime().toSeconds()) {
            event.reply("Your account need to be at least %s old.".formatted(Formatting.duration(trial.serverTime())))
                    .setEphemeral(true)
                    .queue();
            return;
        }

        Product product = optProduct.get();

        if (!optProduct.get().hasTrial(event.getMember())) {
            event.reply("You have no trial left for this product").setEphemeral(true).queue();
            return;
        }

        Optional<MenuEntry<?, ?>> downloadTypeMenu = getDownloadTypeMenu(event.getMember(), product);
        if (downloadTypeMenu.isEmpty()) {
            event.reply("You do not have access to any releases").setEphemeral(true).queue();
            return;
        }

        context.registerMenu(MenuAction.forCallback("Please Choose a build type", event)
                .addComponent(downloadTypeMenu.get())
                .asEphemeral()
                .build());
    }

    private Optional<MenuEntry<?, ?>> getDownloadTypeMenu(Member member, Product product) {
        StringSelectMenu.Builder buildType = StringSelectMenu.create("build_type")
                .setMaxValues(1)
                .setMinValues(1)
                .setPlaceholder("Please choose a build type");

        List<Download> downloads = product.downloads().downloads().stream().filter(d -> d.type().releaseType() == ReleaseType.STABLE).toList();

        if (downloads.isEmpty()) {
            return Optional.empty();
        }

        for (Download download : downloads) {
            DownloadType type = download.type();
            if (type.releaseType() != ReleaseType.STABLE) continue;
            buildType.addOption(type.name(), String.valueOf(type.id()), type.description());
        }

        return Optional.of(MenuEntry.of(buildType.build(),
                ctx -> {
                    // Remove the version selection menu again
                    ctx.container().entries().removeIf(e -> e.id().equals("version"));

                    String typeId = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
                    var downloadType = product.products().licenseGuild().downloadTypes().byId(Integer.parseInt(typeId)).get();

                    var download = product.downloads().byType(downloadType).get();
                    List<AssetXO> assets = download.latestAssets();
                    if (assets.isEmpty()) {
                        ctx.refresh("No build of this type found. Please choose another one.");
                        return;
                    }
                    AssetXO asset = assets.get(0);

                    String filename = "%s-%s.%s".formatted(asset.maven2().artifactId(), asset.maven2().version(), asset.maven2().extension());

                    MessageEmbed build = new EmbedBuilder()
                            .setTitle("📦 " + filename)
                            .addField("Size", humanReadableByteCountSI(asset.fileSize()), true)
                            .addField("Md5", asset.checksum().md5(), true)
                            .addField("Sha256", asset.checksum().sha256(), true)
                            .setColor(Colors.Strong.PINK)
                            .setFooter("This is a one time use link. Do not distribute.")
                            .build();
                    String url = api.v1().download().proxy().registerAsset(new AssetDownload(asset.id(), () -> {
                        download.downloaded(asset.maven2().version());
                        product.claimTrial(member);
                    }));
                    ctx.entry().hidden();

                    ctx.container().entries().add(MenuEntry.of(Button.of(ButtonStyle.LINK, url, "Download", Emoji.fromUnicode("⬇️")), c -> {
                    }));
                    ctx.refresh(build);

                }));
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            var choices = guilds.guild(event.getGuild()).products().completeTrials(focusedOption.getValue());
            event.replyChoices(choices).queue();
        }
    }
}
