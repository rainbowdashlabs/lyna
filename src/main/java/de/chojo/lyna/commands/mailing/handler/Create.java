package de.chojo.lyna.commands.mailing.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.modals.handler.ModalHandler;
import de.chojo.jdautil.modals.handler.TextInputHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseGuild;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;
import net.dv8tion.jda.api.interactions.components.text.TextInputStyle;
import org.slf4j.Logger;

import static org.slf4j.LoggerFactory.getLogger;

public class Create implements SlashHandler {
    private static final Logger log = getLogger(Create.class);
    private final Guilds guilds;

    public Create(Guilds guilds) {
        this.guilds = guilds;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        LicenseGuild guild = guilds.guild(event.getGuild());

        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        var mailName = event.getOption("mail_name", OptionMapping::getAsString);
        var mail = event.getOption("mail", OptionMapping::getAsAttachment);


        if (product.isEmpty()) {
            event.reply("Invalid product").setEphemeral(true).queue();
            return;
        }

        if (mail != null) {
            String mailText;
            try (var download = mail.getProxy().download().join()) {
                mailText = new String(download.readAllBytes());
            } catch (Exception e) {
                log.error("Could not download file", e);
                return;
            }
            product.get().mailings().create(mailName, mailText);
            event.reply("Created").setEphemeral(true).queue();
        } else {
            event.reply("Please provide a address test").setEphemeral(true).queue();
            context.registerModal(ModalHandler.builder("modal").addInput(TextInputHandler.builder("html", "HTML", TextInputStyle.PARAGRAPH)
                            .withHandler(text -> product.get().mailings().create(mailName, text.getAsString())))
                    .withHandler(e -> e.reply("Registered").setEphemeral(true).queue())
                    .build());
        }
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).products().complete(focusedOption.getValue(), false)).queue();
        }
    }
}
