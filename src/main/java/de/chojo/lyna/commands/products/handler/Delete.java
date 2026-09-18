/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.commands.products.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.feature.product.entity.Product;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Delete implements SlashHandler {
    private final Guilds guilds;

    public Delete(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var id = event.getOption("name", OptionMapping::getAsInt);
        Optional<Product> product = guilds.guild(event.getGuild()).products().byId(id);

        if (product.isEmpty()) {
            event.reply("Unknown product").setEphemeral(true).queue();
            return;
        }

        if (product.get().delete()) {
            event.reply("Deleted").setEphemeral(true).queue();
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("name")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue()))
                    .queue();
        }
    }
}
