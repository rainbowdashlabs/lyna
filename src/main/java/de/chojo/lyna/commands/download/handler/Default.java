/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.download.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.product.entity.Product;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

/**
 * Points at the product's page on Lyna, where the download wizard offers every version and build
 * the member may have. Building downloads in Discord duplicated the wizard in a place with no room
 * for it.
 */
public class Default implements SlashHandler {
    private final Guilds guilds;
    private final Api api;

    public Default(Guilds guilds, Api api) {
        this.guilds = guilds;
        this.api = api;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        Optional<Product> product;
        try {
            product =
                    guilds.guild(event.getGuild()).products().byId(event.getOption("product", OptionMapping::getAsInt));
        } catch (NumberFormatException e) {
            product = Optional.empty();
        }
        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }
        event.reply("Download **%s** on Lyna.".formatted(product.get().name()))
                .setEphemeral(true)
                .addComponents(ActionRow.of(Button.link(
                        "%s/products/%d".formatted(api.url(), product.get().id()), "Open on Lyna")))
                .queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            var choices = guilds.guild(event.getGuild())
                    .user(event.getMember())
                    .completeDownloadableProducts(focusedOption.getValue());
            event.replyChoices(choices).queue();
        }
    }
}
