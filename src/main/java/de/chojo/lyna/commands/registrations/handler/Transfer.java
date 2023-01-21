package de.chojo.lyna.commands.registrations.handler;

import de.chojo.jdautil.interactions.slash.structure.handler.SlashHandler;
import de.chojo.jdautil.wrapper.EventContext;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.dao.LicenseUser;
import de.chojo.lyna.data.dao.licenses.License;
import de.chojo.lyna.services.RoleService;
import net.dv8tion.jda.api.events.interaction.command.CommandAutoCompleteInteractionEvent;
import net.dv8tion.jda.api.events.interaction.command.SlashCommandInteractionEvent;
import net.dv8tion.jda.api.interactions.AutoCompleteQuery;
import net.dv8tion.jda.api.interactions.commands.OptionMapping;

import java.util.Optional;

public class Transfer implements SlashHandler {
    private final Guilds guilds;
    private final RoleService roleService;

    public Transfer(Guilds guilds, RoleService roleService) {
        this.guilds = guilds;
        this.roleService = roleService;
    }

    @Override
    public void onSlashCommand(SlashCommandInteractionEvent event, EventContext context) {
        var guild = guilds.guild(event.getGuild());
        LicenseUser user = guild.user(event.getMember());

        var product = guild.products().byId(event.getOption("product", OptionMapping::getAsInt));
        if (product.isEmpty()) {
            event.reply("Unkown product").setEphemeral(true).queue();
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

        roleService.unclaim(license.get());
        license.get().clearSubUsers();

        license.get().transfer(target);
        roleService.claim(license.get());

        event.reply("License transfered").setEphemeral(true).queue();
        target.getUser().openPrivateChannel().complete()
              .sendMessage("A license for %s was transfered to you by %s.".formatted(
                      product.get().name(), event.getMember().getAsMention()))
              .queue();
    }

    @Override
    public void onAutoComplete(CommandAutoCompleteInteractionEvent event, EventContext context) {
        AutoCompleteQuery focusedOption = event.getFocusedOption();
        if (focusedOption.getName().equals("product")) {
            event.replyChoices(guilds.guild(event.getGuild()).user(event.getMember()).completeProducts(focusedOption.getValue())).queue();
        }
    }
}
