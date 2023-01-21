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

import java.util.List;
import java.util.Optional;

public class Add implements SlashHandler {
    private final Guilds guilds;

    public Add(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        LicenseUser licenseUser = guild.user(event.getMember());
        Optional<Product> product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        Member target = event.getOption("user", OptionMapping::getAsMember);
        if (product.isEmpty()) {
            event.reply("Unkown product").setEphemeral(true).queue();
            return;
        }

        Optional<License> license = licenseUser.licenseByProduct(product.get());
        if (license.isEmpty()) {
            event.reply("You don't have a license for this product.").setEphemeral(true).queue();
            return;
        }

        if (product.get().canAccess(target)) {
            product.get().assign(target);
            event.reply("This user has already access to this product.").setEphemeral(true).queue();
            return;
        }

        List<Long> subUsers = license.get().subUsers();
        if (subUsers.contains(target.getIdLong())) {
            event.reply("This user has already access to your license.").setEphemeral(true).queue();
            product.get().assign(target);
            return;
        }

        if (subUsers.size() >= guild.settings().license().shares()) {
            event.reply("You have reached the share limit.").setEphemeral(true).queue();
            return;
        }

        license.get().addSubUser(target);
        event.reply("Access granted.").setEphemeral(true).queue();
        target.getUser().openPrivateChannel().complete()
              .sendMessage("%s shared their %s license with you."
                      .formatted(event.getMember().getAsMention(), product.get().name()))
              .queue();
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
