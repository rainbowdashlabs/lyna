package de.chojo.lyna.commands.download.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.menus.MenuAction;
import de.chojo.jdautil.menus.entries.MenuEntry;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.api.Api;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.downloadtype.DownloadType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.data.dao.products.downloads.Download;
import de.chojo.nexus.entities.AssetXO;
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

        Product product = optProduct.get();

        if (!optProduct.get().canAccess(event.getMember())) {
            event.reply("You do not have access to this product").queue();
            return;
        }


        // Hello future me. This stuff is cursed, so I try to document it a bit c:
        // Create all possible entries and hide what you do not need yet
        context.registerMenu(MenuAction.forCallback("Please Choose a build type", event)
                .addComponent(getDownloadTypeMenu(product))
                .asEphemeral()
                .build());
    }

    private MenuEntry<?, ?> getDownloadTypeMenu(Product product) {
        StringSelectMenu.Builder buildType = StringSelectMenu.create("build_type")
                .setMaxValues(1)
                .setMinValues(1)
                .setPlaceholder("Please choose a build type");

        List<Download> downloads = product.downloads().downloads();


        for (Download download : downloads) {
            DownloadType type = download.type();
            buildType.addOption(type.name(), String.valueOf(type.id()), type.description());
        }

        return MenuEntry.of(buildType.build(),
                ctx -> {
                    // Remove the version selection menu again
                    ctx.container().entries().removeIf(e -> e.id().equals("version"));

                    String typeId = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
                    var downloadType = product.products().licenseGuild().downloadTypes().byId(Integer.parseInt(typeId)).get();
                    var versionMenu = getVersionMenu(product, downloadType);
                    if (versionMenu.isEmpty()) {
                        ctx.refresh("No build of this type found. Please choose another one.");
                        return;
                    }
                    ctx.container().entries().add(versionMenu.get());
                    // hide the build type
                    ctx.entry().hidden();
                    ctx.refresh("Please choose a version");
                });
    }

    private Optional<MenuEntry<?, ?>> getVersionMenu(Product product, DownloadType downloadType) {
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
                "Published: " + asset.lastModified().format(DateTimeFormatter.ofPattern("yy-MM-dd HH:mm"))));

        return Optional.of(MenuEntry.of(versionMenu.build(), ctx -> {
            String version = ctx.event().getInteraction().getSelectedOptions().get(0).getValue();
            String url = api.v1().download().proxy().registerAsset(version);
            ctx.container().entries().add(MenuEntry.of(Button.of(ButtonStyle.LINK, url, "Download", Emoji.fromUnicode("⬇️")), c -> {
            }));
            ctx.entry().hidden();

            ctx.refresh("Click to download. This is a one time use link. Do not distribute.");
        }));
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            var choices = guilds.guild(event.getGuild()).user(event.getMember())
                    .completeOwnProducts(focusedOption.getValue());
            event.replyChoices(choices).queue();
        }
    }
}
