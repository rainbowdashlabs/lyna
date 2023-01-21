package de.chojo.lyna.commands.registrations.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.util.Colors;
import de.chojo.jdautil.util.MentionUtil;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.Product;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

public class Info implements SlashHandler {
    private final Guilds guilds;

    public Info(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        LicenseUser licenseUser = guild.user(event.getMember());
        Optional<Product> product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        if (product.isEmpty()) {
            event.reply("Unkown product.").setEphemeral(true).queue();
            return;
        }
        if (!licenseUser.canAccess(product.get())) {
            event.reply("You have no access to this product.").queue();
            return;
        }

        Optional<License> optLicense = licenseUser.licenseByProduct(product.get());
        if (optLicense.isPresent()) {
            License license = optLicense.get();
            var builder = new EmbedBuilder()
                    .setTitle(product.get().name(), product.get().url())
                    .setAuthor("Purchased via " + license.platform().name(), license.platform().url())
                    .setColor(Colors.Pastel.DARK_PINK)
                    .addField("Key", "|| %s ||".formatted(license.key()), true);

            List<Long> subUsers = license.subUsers();
            if (!subUsers.isEmpty()) {
                var shared = subUsers.stream()
                                     .map(id -> "%s (%s)".formatted(MentionUtil.user(id), id))
                                     .collect(Collectors.joining("\n"));
                builder.addField("Shared with:", shared, true);
            }
            event.replyEmbeds(builder.build()).setEphemeral(true).queue();
            return;
        }

        optLicense = licenseUser.subLicenseByProduct(product.get());
        if (optLicense.isPresent()) {
            License license = optLicense.get();
            var builder = new EmbedBuilder()
                    .setTitle(product.get().name(), product.get().url())
                    .setAuthor("Shared license")
                    .setColor(Colors.Pastel.DARK_PINK)
                    .addField("License owner:", MentionUtil.user(license.owner()), true);
            event.replyEmbeds(builder.build()).setEphemeral(true).queue();
            return;
        }
        event.reply("Could not find any license.").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            var choices = guilds.guild(event.getGuild()).user(event.getMember())
                                .completeAllProducts(focusedOption.getValue());
            event.replyChoices(choices).queue();
        }
    }

}
