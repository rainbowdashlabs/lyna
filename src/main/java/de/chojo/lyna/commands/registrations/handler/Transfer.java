package de.chojo.lyna.commands.registrations.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.licenses.License;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Transfer implements SlashHandler {
    private final Guilds guilds;

    public Transfer(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        LicenseUser user = guild.user(event.getMember());

        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        if (product.isEmpty()) {
            event.reply("Unknown product").setEphemeral(true).queue();
            return;
        }
        Optional<License> license = user.licenseByProduct(product.get());

        if (license.isEmpty()) {
            event.reply("You don't have a license for this product").setEphemeral(true).queue();
            return;
        }

        var target = event.getOption("user", OptionMapping::getAsMember);

        if (guild.user(target).licenseByProduct(product.get()).isPresent()) {
            event.reply("This user has a license for this product already.").setEphemeral(true).queue();
            return;
        }

        license.get().transfer(target);

        event.reply("License transferred").setEphemeral(true).queue();
        target.getUser().openPrivateChannel().complete()
              .sendMessage("A license for %s was transferred to you by %s.".formatted(
                      product.get().name(), event.getMember().getAsMention()))
              .queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).user(event.getMember()).completeOwnProducts(focusedOption.getValue())).queue();
        }
    }
}
