package de.chojo.lyna.commands.download.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.menus.EntryContext;
import de.chojo.jdautil.menus.MenuAction;
import de.chojo.jdautil.menus.entries.MenuEntry;
import de.chojo.jdautil.util.Colors;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.lyna.web.api.Api;
import de.chojo.lyna.web.api.v1.download.proxy.AssetDownload;
import de.chojo.nexus.entities.AssetXO;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.emoji.Emoji;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.events.interaction.component.StringSelectInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.buttons.Button;
import net.dv8tion.jda.api.interactions.components.buttons.ButtonStyle;
import net.dv8tion.jda.api.interactions.components.selections.StringSelectMenu;

import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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
        Optional<Product> optProduct;
        try {
            optProduct = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        } catch (NumberFormatException e) {
            event.reply("Invalid Product. Please use the auto completion.").setEphemeral(true).queue();
            return;
        }
        if (optProduct.isEmpty()) {
            event.reply("Invalid Product").setEphemeral(true).queue();
            return;
        }

        Product product = optProduct.get();

        if (!optProduct.get().canDownload(event.getMember())) {
            event.reply("You do not have access to this product").setEphemeral(true).queue();
            return;
        }

        Optional<MenuEntry<?, ?>> downloadTypeMenu = getReleaseTypeMenu(event.getMember(), product);
        if (downloadTypeMenu.isEmpty()) {
            event.reply("You do not have access to any releases").setEphemeral(true).queue();
            return;
        }

        context.registerMenu(MenuAction.forCallback("Please Choose a build download", event)
                                       .addComponent(downloadTypeMenu.get())
                                       .asEphemeral()
                                       .build());
    }

    private Optional<MenuEntry<?, ?>> getReleaseTypeMenu(Member member, Product product) {
        // First we show the available release types. Disregarding the Download Type. This will be the last step.
        StringSelectMenu.Builder buildType = StringSelectMenu.create("build_type")
                                                             .setMaxValues(1)
                                                             .setMinValues(1)
                                                             .setPlaceholder("Please choose a build download");

        Set<ReleaseType> access = product.availableReleaseTypes(member);
        List<Download> downloads = product.downloads().downloads().stream().filter(d -> access.contains(d.type().releaseType())).sorted().toList();

        if (downloads.isEmpty()) {
            return Optional.empty();
        }

        List<ReleaseType> available = new ArrayList<>();
        for (Download download : downloads) {
            DownloadType type = download.type();
            if (!access.contains(type.releaseType())) continue;
            available.add(type.releaseType());
        }

        available = available.stream().sorted().distinct().toList();
        if (available.size() == 1) {
            var releases = product.downloads().byReleaseType(available.stream().findFirst().get());
            return getVersionMenu(member, product, releases);
        }
        available.forEach(type -> buildType.addOption(type.name(), type.name()));

        return Optional.of(MenuEntry.of(buildType.build(),
                ctx -> {
                    // Now that we have the release download we want the available versions of that release download
                    ctx.container().entries().removeIf(e -> e.id().equals("version"));

                    String releaseType = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
                    var releases = product.downloads().byReleaseType(ReleaseType.parse(releaseType));
                    var versionMenu = getVersionMenu(member, product, releases);
                    if (versionMenu.isEmpty()) {
                        ctx.refresh("No build of this download found. Please choose another one.");
                        return;
                    }
                    ctx.container().entries().add(versionMenu.get());
                    // hide the build download
                    ctx.entry().hidden();
                    ctx.refresh("Please choose a version");
                }));
    }

    private Optional<MenuEntry<?, ?>> getVersionMenu(Member member, Product product, List<Download> download) {
        // We want to have all the available versions of any download.

        StringSelectMenu.Builder versionMenu = StringSelectMenu.create("version")
                                                               .setMinValues(1)
                                                               .setMaxValues(1)
                                                               .setPlaceholder("Please choose a version");

        List<AssetXO> assets = download.stream().map(Download::latestAssets).flatMap(List::stream)
                                       .sorted((a, b) -> b.lastModified().compareTo(a.lastModified()))
                                       .toList();
        assets = new ArrayList<>(assets);
        Collections.reverse(assets);
        if (assets.isEmpty()) {
            return Optional.empty();
        }

        Set<String> versions = new HashSet<>();
        assets = assets.stream().filter(a -> {
            if (versions.contains(a.maven2().version())) return false;
            versions.add(a.maven2().version());
            return true;
        }).toList();
        // We add all available versions to the menu (newest 25)
        assets.reversed().stream().limit(25).forEach(asset -> versionMenu.addOption(asset.maven2().version(),
                asset.maven2().version(),
                "Published: " + asset.lastModified().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        return Optional.of(MenuEntry.of(versionMenu.build(), ctx -> {
            // Now we have a version. We now need to check which download has this version
            String version = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();

            // TODO: this might be worth running in parallel, although there are usually one or two downloads.
            var available = download.stream()
                                    .map(d -> new AssetVersion(d, d.assetByVersion(version).orElse(null)))
                                    .filter(a -> a.asset != null)
                                    .toList();

            if (available.size() == 1) {
                registerButton(available.getFirst(), member, ctx);
                return;
            }

            Optional<MenuEntry<?, ?>> downloadMenu = getDownloadMenu(member, available);
            if (downloadMenu.isEmpty()) {
                ctx.refresh("No build of this version found. Please choose another one.");
                return;
            }
            ctx.container().entries().removeIf(e -> e.id().equals("download"));
            ctx.container().entries().add(downloadMenu.get());
            ctx.entry().hidden();
            ctx.refresh("Please choose a version");
        }));
    }

    private Optional<MenuEntry<?, ?>> getDownloadMenu(Member member, List<AssetVersion> available) {
        StringSelectMenu.Builder downloadMenu = StringSelectMenu.create("download")
                                                                .setPlaceholder("Please choose a type");
        available.forEach(a -> downloadMenu.addOption(a.download.type().name(), a.asset.id(), a.download().type().description()));

        return Optional.of(MenuEntry.of(downloadMenu.build(), ctx -> {
            var id = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
            Optional<AssetVersion> first = available.stream().filter(d -> d.asset.id().equals(id)).findFirst();
            registerButton(first.get(), member, ctx);
        }));
    }

    private void registerButton(AssetVersion assetVersion, Member member, EntryContext<StringSelectInteractionEvent, StringSelectMenu> ctx) {
        AssetXO asset = assetVersion.asset();
        String url = api.v1().download().proxy()
                        .registerAsset(new AssetDownload(asset.id(),
                                () -> assetVersion.download().downloaded(asset.maven2().version())
                                , "%s(%s)".formatted(member.getUser().getName(), member.getId())));
        ctx.container().entries().add(MenuEntry.of(Button.of(ButtonStyle.LINK, url, "Download", Emoji.fromUnicode("⬇️")), c -> {
        }));
        ctx.entry().hidden();
        String filename = "%s-%s.%s".formatted(asset.maven2().artifactId(), asset.maven2().version(), asset.maven2().extension());

        MessageEmbed build = new EmbedBuilder()
                .setTitle("📦 " + filename)
                .addField("Size", humanReadableByteCountSI(asset.fileSize()), true)
                // TODO: Files are individual per user. Therefore a static hash can't be used.
                //.addField("Md5", asset.checksum().md5(), true)
                //.addField("Sha256", asset.checksum().sha256(), true)
                .setColor(Colors.Strong.PINK)
                .setFooter("This is a one time use link. Do not distribute.")
                .build();
        ctx.refresh(build);
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            var choices = guilds.guild(event.getGuild()).user(event.getMember())
                                .completeDownloadableProducts(focusedOption.getValue());
            event.replyChoices(choices).queue();
        }
    }

    private record AssetVersion(Download download, AssetXO asset) {

    }
}
