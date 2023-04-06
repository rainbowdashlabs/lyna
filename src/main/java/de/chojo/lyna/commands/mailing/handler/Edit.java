package de.chojo.lyna.commands.mailing.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import de.chojo.lyna.data.dao.products.mailings.Mailing;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import org.slf4j.Logger;

import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

public class Edit implements SlashHandler {
    private static final Logger log = getLogger(Edit.class);
    private final Guilds guilds;

    public Edit(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());
        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var platform = guild.platforms().byId(event.getOption("platform", OptionMapping::getAsInt));
        var mailName = event.getOption("mail_name", OptionMapping::getAsString);
        var mail = event.getOption("mail", OptionMapping::getAsAttachment);

        if (platform.isEmpty()) {
            event.reply("Invalid platform").setEphemeral(true).queue();
            return;
        }

        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        Optional<Mailing> optMailing = product.get().mailings().byPlatform(platform.get());
        if (optMailing.isEmpty()) {
            event.reply("No mailing found for this product and platform").queue();
            return;
        }

        Mailing mailing = optMailing.get();
        if (mail != null) {
            String mailText;
            try (var download = mail.getProxy().download().join()) {
                mailText = new String(download.readAllBytes());
            } catch (Exception e) {
                log.error("Could not download file", e);
                return;
            }

            mailing.mailText(mailText);
        }

        if (mailName != null) {
            mailing.name(mailName);
        }

        event.reply("Updated").setEphemeral(true).queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue(), false)).queue();
        }
        if (focusedOption.getName().equals("platform")) {
            event.replyChoices(guilds.guild(event.getGuild()).platforms().complete(focusedOption.getValue())).queue();
        }
    }
}
