package de.chojo.lyna.commands.download.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.menus.MenuAction;
import de.chojo.jdautil.menus.entries.MenuEntry;
import de.chojo.jdautil.util.Colors;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.web.api.Api;
import de.chojo.lyna.web.api.v1.download.proxy.AssetDownload;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
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

import java.time.format.DateTimeFormatter;
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

        Set<ReleaseType> access = product.availableReleaseTypes(member);
        List<Download> downloads = product.downloads().downloads().stream().filter(d -> access.contains(d.type().releaseType())).sorted().toList();

        if (downloads.isEmpty()) {
            return Optional.empty();
        }

        for (Download download : downloads) {
            DownloadType type = download.type();
            if (!access.contains(type.releaseType())) continue;
            buildType.addOption(type.name(), String.valueOf(type.id()), type.description());
        }

        return Optional.of(MenuEntry.of(buildType.build(),
                ctx -> {
                    // Remove the version selection menu again
                    ctx.container().entries().removeIf(e -> e.id().equals("version"));

                    String typeId = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
                    var downloadType = product.products().licenseGuild().downloadTypes().byId(Integer.parseInt(typeId)).get();
                    var versionMenu = getVersionMenu(member,product, downloadType);
                    if (versionMenu.isEmpty()) {
                        ctx.refresh("No build of this type found. Please choose another one.");
                        return;
                    }
                    ctx.container().entries().add(versionMenu.get());
                    // hide the build type
                    ctx.entry().hidden();
                    ctx.refresh("Please choose a version");
                }));
    }

    private Optional<MenuEntry<?, ?>> getVersionMenu(Member member, Product product, DownloadType downloadType) {
        var download = product.downloads().byType(downloadType).get();
        StringSelectMenu.Builder versionMenu = StringSelectMenu.create("version")
                .setMinValues(1)
                .setMaxValues(1)
                .setPlaceholder("Please choose a version");

        List<AssetXO> assets = download.latestAssets();
        if (assets.isEmpty()) {
            return Optional.empty();
        }
        assets.stream().limit(25).forEach(asset -> versionMenu.addOption(asset.maven2().version(),
                asset.id(),
                "Published: " + asset.lastModified().format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"))));

        return Optional.of(MenuEntry.of(versionMenu.build(), ctx -> {
            String assetId = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
            AssetXO asset = assets.stream().filter(assetXO -> assetXO.id().equals(assetId)).findFirst().get();
            String url = api.v1().download().proxy()
                    .registerAsset(new AssetDownload(assetId, () -> download.downloaded(asset.maven2().version()), "%s(%s)".formatted(member.getUser().getName(), member.getId())));
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
        }));
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
}
