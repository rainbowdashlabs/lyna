package de.chojo.lyna.commands.mailing.handler;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import de.chojo.lyna.mail.Mail;
import de.chojo.lyna.mail.MailCreator;
import de.chojo.lyna.mail.MailingService;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Send implements SlashHandler {
    private final MailingService mailingService;
    private final Configuration<ConfigFile> configuration;
    private final Guilds guilds;

    public Send(MailingService mailingService, Configuration<ConfigFile> configuration, Guilds guilds) {
        this.mailingService = mailingService;
        this.configuration = configuration;
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var address = event.getOption("address", OptionMapping::getAsString);
        var name = event.getOption("name", OptionMapping::getAsString);

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        Optional<Mailing> optMailing = product.get().mailings().get();
        if (optMailing.isEmpty()) {
            event.reply("No mailing found for this product").queue();
            return;
        }



        Optional<License> license = product.get().createLicense(address);

        if (license.isEmpty()) {
            event.reply("A license does already exist for this address").setEphemeral(true).queue();
            return;
        }

        license.get().grantAccess(ReleaseType.STABLE);

        Mailing mailing = optMailing.get();
        Mail mail = MailCreator.createLicenseMessage(mailing, license.get().key(), name, address);

        mailingService.sendMail(mail);
        event.reply("Email sent").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue(), false)).queue();
        }
    }
}
