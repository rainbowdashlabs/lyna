package de.chojo.lyna.commands.registrations.handler.share;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Remove implements SlashHandler {
    private final Guilds guilds;

    public Remove(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        Optional<Product> product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        LicenseUser licenseUser = guild.user(event.getMember());
        Member target = event.getOption("user", OptionMapping::getAsMember);
        if (product.isEmpty()) {
            event.reply("Unkown product").setEphemeral(true).queue();
            return;
        }

        Optional<License> license = licenseUser.licenseByProduct(product.get());

        if (license.get().removeSubUser(target)) {
            event.reply("Access to your license was revoked.").setEphemeral(true).queue();
            target.getUser().openPrivateChannel().complete()
                  .sendMessage("Your access to the %s license of %s was revoked."
                          .formatted(product.get().name(), event.getMember().getAsMention())).queue();
        } else {
            event.reply("This user has no access to your license.").setEphemeral(true).queue();
        }
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
