package de.chojo.lyna.core;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Nexus;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.data.access.RevokedJtis;
import de.chojo.nexus.NexusRest;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.slf4j.Logger;

import java.io.IOException;
import java.sql.SQLException;

import static org.slf4j.LoggerFactory.getLogger;

public class Data {
    private static final Logger log = getLogger(Data.class);
    private final Threading threading;
    private final Configuration<ConfigFile> configuration;
    private HikariDataSource dataSource;
    private Guilds guilds;
    private Products products;
    private NexusRest nexus;
    private Mailings mailings;
    private KoFiProducts kofi;
    private Accounts accounts;
    private AccountSessions accountSessions;
    private RevokedJtis revokedJtis;
    private DownloadLog downloadLog;
    private InstanceSettingsAccess instanceSettings;
    private PasswordHasher passwordHasher;
    private JwtService jwtService;
    private DiscordOAuthClient discordOAuthClient;

    private Data(Threading threading, Configuration<ConfigFile> configuration) {
        this.threading = threading;
        this.configuration = configuration;
    }

    public static Data create(Threading threading, Configuration<ConfigFile> configuration) throws SQLException, IOException, InterruptedException {
        var data = new Data(threading, configuration);
        data.init();
        return data;
    }

    public void init() throws SQLException, IOException, InterruptedException {
        initConnection();
        configure();
        updateDatabase();
        initDao();
    }
    public void initConnection() {
        try {
            dataSource = getConnectionPool();
        } catch (Exception e) {
            log.error("Could not connect to database. Retrying in 10.");
            try {
                Thread.sleep(1000 * 10);
            } catch (InterruptedException ignore) {
            }
            initConnection();
        }
    }

    private void updateDatabase() throws IOException, SQLException {
        var schema = configuration.config().database().schema();
        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("lyna", schema))
                .setVersionTable(schema + ".lyna_version")
                .setSchemas(schema)
                .execute();
    }

    private void configure() {
        log.info("Configuring QueryBuilder");
        var logger = getLogger("DbLogger");
        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource)
                .setExceptionHandler(err -> logger.error(LogNotify.NOTIFY_ADMIN, "An error occurred during a database request", err))
                .build());
    }

    private void initDao() {
        log.info("Creating DAOs");
        Nexus nexus = configuration.config().nexus();
        this.nexus = NexusRest.builder(nexus.host())
                .setPasswordAuth(nexus.username(), nexus.password())
                .build();
        guilds = new Guilds(this.nexus, configuration);
        products = new Products(this.guilds);
        mailings = new Mailings(this.guilds);
        kofi = new KoFiProducts(products);
        accounts = new Accounts();
        accountSessions = new AccountSessions();
        revokedJtis = new RevokedJtis();
        downloadLog = new DownloadLog();
        instanceSettings = new InstanceSettingsAccess();
        passwordHasher = new PasswordHasher();
        jwtService = new JwtService(configuration.config().auth());
        discordOAuthClient = new DiscordOAuthClient(configuration.config().discord().oauth());
    }

    private HikariDataSource getConnectionPool() {
        log.info("Creating connection pool.");
        var data = configuration.config().database();
        return DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config
                        .host(data.host())
                        .port(data.port())
                        .user(data.user())
                        .password(data.password())
                        .database(data.database()))
                .create()
                .withMaximumPoolSize(data.poolSize())
                .withThreadFactory(Threading.createThreadFactory(threading.hikariGroup()))
                .forSchema(data.schema())
                .build();
    }

    public void shutDown() {
        dataSource.close();
    }

    public HikariDataSource dataSource() {
        return dataSource;
    }

    public Guilds guilds() {
        return guilds;
    }

    public NexusRest nexus() {
        return nexus;
    }

    public void inject(Bot bot) {
        products.shardManager(bot.shardManager());
        mailings.shardManager(bot.shardManager());
    }

    public void injectShard(Bot bot, de.chojo.lyna.web.api.Api api) {
        api.shardManager(bot.shardManager());
    }

    public KoFiProducts kofi() {
        return kofi;
    }

    public Products products() {
        return products;
    }

    public Mailings mailings() {
        return mailings;
    }

    public Accounts accounts() {
        return accounts;
    }

    public AccountSessions accountSessions() {
        return accountSessions;
    }

    public RevokedJtis revokedJtis() {
        return revokedJtis;
    }

    public DownloadLog downloadLog() {
        return downloadLog;
    }

    public InstanceSettingsAccess instanceSettings() {
        return instanceSettings;
    }

    public PasswordHasher passwordHasher() {
        return passwordHasher;
    }

    public JwtService jwtService() {
        return jwtService;
    }

    public DiscordOAuthClient discordOAuthClient() {
        return discordOAuthClient;
    }
}
