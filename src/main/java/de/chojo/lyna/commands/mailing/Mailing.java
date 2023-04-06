package de.chojo.lyna.commands.mailing;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.commands.mailing.handler.Create;
import de.chojo.lyna.data.access.Guilds;

public class Mailing extends SlashCommand {
    public Mailing(Guilds guilds) {
        super(Slash.of("mailing", "Configure mailing")
                .unlocalized()
                .adminCommand()
                .subCommand(SubCommand.of("create", "Create a new mailing setting")
                        .handler(new Create(guilds))
                        .argument(Argument.text("product", "product").asRequired().withAutoComplete())
                        .argument(Argument.text("platform", "platform").asRequired().withAutoComplete())
                        .argument(Argument.text("mail_name", "Mail of product in the mail"))));
    }
}
