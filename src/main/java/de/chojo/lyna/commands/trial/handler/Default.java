/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.trial.handler;

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
 * Points at the product's trial on Lyna. The page holds a Discord member to the same rules this
 * command did once their Discord account is linked, and records the trial against both, so the two
 * cannot be taken one after the other.
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
        if (product.isEmpty() || !product.get().trial()) {
            event.reply("This product offers no trial").setEphemeral(true).queue();
            return;
        }
        event.reply("Try **%s** once on Lyna. Sign in with Discord to take it."
                        .formatted(product.get().name()))
                .setEphemeral(true)
                .addComponents(ActionRow.of(Button.link(
                        "%s/products/%d?trial=1"
                                .formatted(api.url(), product.get().id()),
                        "Take the trial")))
                .queue();
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
