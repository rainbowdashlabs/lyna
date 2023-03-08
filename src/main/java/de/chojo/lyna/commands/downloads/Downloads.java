package de.chojo.lyna.commands.downloads;

import de.chojo.jdautil.interactions.slash.Argument;
import de.chojo.jdautil.interactions.slash.Group;
import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.SubCommand;
import de.chojo.jdautil.interactions.slash.provider.SlashCommand;
import de.chojo.lyna.commands.downloads.handler.download.CreateDownload;
import de.chojo.lyna.commands.downloads.handler.type.CreateType;
import de.chojo.lyna.commands.downloads.handler.type.DeleteType;
import de.chojo.lyna.commands.downloads.handler.type.EditType;
import de.chojo.lyna.data.access.Guilds;

public class Downloads extends SlashCommand {
    public Downloads(Guilds guilds) {
        super(Slash.of("downloads", "Manage downloads")
                .guildOnly()
                .unlocalized()
                .adminCommand()
                .group(Group.of("type", "Manage download types")
                        .subCommand(SubCommand.of("create", "Create a download type")
                                .handler(new CreateType(guilds))
                                .argument(Argument.text("name", "Type name").asRequired())
                                .argument(Argument.text("description", "Type description").asRequired())
                                .argument(Argument.text("release_type", "Release type").asRequired().withAutoComplete())
                        )
                        .subCommand(SubCommand.of("delete", "delete a download type")
                                .handler(new DeleteType(guilds))
                                .argument(Argument.text("name", "Type name").asRequired().withAutoComplete())
                        )
                        .subCommand(SubCommand.of("edit", "Edit a download type")
                                .handler(new EditType(guilds))
                                .argument(Argument.text("name", "Type name").asRequired().withAutoComplete())
                                .argument(Argument.text("new_name", "New name of type"))
                                .argument(Argument.text("new_description", "New description of type"))
                        )
                )
                .group(Group.of("download", "Manage downloads")
                        .subCommand(SubCommand.of("create", "Create a download")
                                .handler(new CreateDownload(guilds))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("type", "Download type").withAutoComplete().asRequired())
                                .argument(Argument.text("repository", "Group id of download").asRequired())
                                .argument(Argument.text("group_id", "Group id of download").asRequired())
                                .argument(Argument.text("artifact_id", "Group id of download").asRequired())
                                .argument(Argument.text("classifier", "Group id of download"))
                        )
                        .subCommand(SubCommand.of("edit", "Edit a download")
                                .handler(new EditType(guilds))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("type", "Download type").withAutoComplete().asRequired())
                                .argument(Argument.text("repository", "Group id of download"))
                                .argument(Argument.text("group_id", "Group id of download"))
                                .argument(Argument.text("artifact_id", "Group id of download"))
                                .argument(Argument.text("classifier", "Group id of download"))
                        )
                        .subCommand(SubCommand.of("delete", "Edit a download")
                                .handler(new DeleteType(guilds))
                                .argument(Argument.text("product", "Product name").withAutoComplete().asRequired())
                                .argument(Argument.text("type", "Download type").withAutoComplete().asRequired())
                        )
                )
                .build());
    }
}
