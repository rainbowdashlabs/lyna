package de.chojo.lyna.core;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.jdautil.interactions.dispatching.InteractionHub;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.commands.license.License;
import de.chojo.lyna.commands.platform.Platform;
import de.chojo.lyna.commands.product.Product;
import de.chojo.lyna.commands.register.Register;
import de.chojo.lyna.commands.registrations.Registrations;
import de.chojo.lyna.commands.settings.Settings;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.services.RoleService;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.apache.commons.codec.language.bm.Rule;
import org.slf4j.Logger;

import java.util.Collections;

import static org.slf4j.LoggerFactory.getLogger;

public class Bot {
    private static final Logger log = getLogger(Bot.class);
    private final Data data;
    private final Threading threading;
    private final Configuration<ConfigFile> configuration;
    private ShardManager shardManager;
    private RoleService roleService;

    private Bot(Data data, Threading threading, Configuration<ConfigFile> configuration) {
        this.data = data;
        this.threading = threading;
        this.configuration = configuration;
    }

    public static Bot create(Data data, Threading threading, Configuration<ConfigFile> configuration) {
        Bot bot = new Bot(data, threading, configuration);
        bot.init();
        return bot;
    }

    private void init() {
        initShardManager();
        initServices();
        initInteractions();
    }

    private void initServices() {
        roleService = new RoleService();
    }

    private void initShardManager() {
        shardManager = DefaultShardManagerBuilder
                .createDefault(configuration.config().baseSettings().token())
                .enableIntents(GatewayIntent.DIRECT_MESSAGES)
                .setEnableShutdownHook(false)
                .setThreadFactory(Threading.createThreadFactory(threading.jdaGroup()))
                .setEventPool(threading.jdaWorker())
                .build();
    }

    private void initInteractions() {
        InteractionHub.builder(shardManager)
                .testMode("true".equals(System.getProperty("bot.testmode", "false")))
                .cleanGuildCommands("true".equals(System.getProperty("bot.cleancommand", "false")))
                .withCommandErrorHandler((context, throwable) -> {
                    log.error(LogNotify.NOTIFY_ADMIN, "Command execution of {} failed\n{}",
                            context.interaction().meta().name(), context.args(), throwable);
                })
                .withGuildCommandMapper(cmd -> Collections.singletonList(configuration.config().baseSettings()
                                                                                      .botGuild()))
                .withDefaultMenuService()
                .withPagination(builder -> builder.previousText("Previous").nextText("Next"))
                .withCommands(
                        new Product(data.guilds()),
                        new Platform(data.guilds()),
                        new License(data.guilds(), configuration, roleService),
                        new Register(data.guilds(), roleService),
                        new Registrations(data.guilds(), roleService),
                        new Settings(data.guilds()))
                .build();
    }
}
