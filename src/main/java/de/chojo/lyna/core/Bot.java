package de.chojo.lyna.core;

import de.chojo.lyna.data.access.Guilds;
import com.google.inject.Inject;
import de.chojo.jdautil.interactions.dispatching.InteractionHub;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.services.RoleListener;
import net.dv8tion.jda.api.requests.GatewayIntent;
import net.dv8tion.jda.api.sharding.DefaultShardManagerBuilder;
import net.dv8tion.jda.api.sharding.ShardManager;
import org.slf4j.Logger;

import de.chojo.jdautil.interactions.slash.Slash;
import de.chojo.jdautil.interactions.slash.provider.SlashProvider;

import java.util.Collections;
import java.util.Set;

import static org.slf4j.LoggerFactory.getLogger;

public class Bot {
    private static final Logger log = getLogger(Bot.class);
    private final Guilds guilds;
    private final Threading threading;
    private final Conf configuration;
    private final Set<SlashProvider<Slash>> commands;
    private final MailingService mailingService;
    private ShardManager shardManager;

    @Inject
    public Bot(Guilds guilds, Threading threading, Conf configuration, Set<SlashProvider<Slash>> commands,
               MailingService mailingService) {
        this.guilds = guilds;
        this.threading = threading;
        this.configuration = configuration;
        this.commands = commands;
        this.mailingService = mailingService;
    }

    /**
     * Connects to Discord and registers the commands, unless this deployment has no bot.
     *
     * <p>Separate from construction: opening a gateway is not something to do while an injector is
     * still assembling the object graph, and everything that asks the bot questions goes through
     * {@link de.chojo.lyna.gateway.Gateway}, which answers empty until this has run.
     */
    public void start() {
        if (!configuration.main().baseSettings().botEnabled()) {
            log.info("Discord bot is disabled. Only the HTTP API is served.");
            return;
        }
        initShardManager();
        initServices();
        initInteractions();
    }

    private void initServices() {
    }

    private void initShardManager() {
        shardManager = DefaultShardManagerBuilder
                .createDefault(configuration.main().baseSettings().token())
                .enableIntents(GatewayIntent.DIRECT_MESSAGES, GatewayIntent.GUILD_MEMBERS)
                .setEnableShutdownHook(false)
                .setThreadFactory(Threading.createThreadFactory(threading.jdaGroup()))
                .setEventPool(threading.jdaWorker())
                .addEventListeners(new RoleListener(guilds))
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
                .withGuildCommandMapper(cmd -> Collections.singletonList(configuration.main().baseSettings()
                        .botGuild()))
                .withDefaultMenuService()
                .withPagination(builder -> builder.previousText("Previous").nextText("Next"))
                .withDefaultModalService()
                .withCommands(commands.toArray(SlashProvider[]::new))
                .build();
    }

    public ShardManager shardManager() {
        return shardManager;
    }
}
