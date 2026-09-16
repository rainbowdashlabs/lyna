package de.chojo.lyna.core;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.auth.DiscordOAuthClient;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.auth.PasswordHasher;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.configuration.elements.Nexus;
import de.chojo.lyna.data.access.AccountLicenses;
import de.chojo.lyna.data.access.LicenseInvites;
import de.chojo.lyna.data.access.AccountSessions;
import de.chojo.lyna.data.access.Accounts;
import de.chojo.lyna.data.access.DemoArtifacts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.lyna.data.access.EmailVerificationTokens;
import de.chojo.lyna.data.access.InstanceOperators;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.KioskProducts;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.access.Mailings;
import de.chojo.lyna.data.access.PasswordResetTokens;
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
import java.time.Duration;

import static org.slf4j.LoggerFactory.getLogger;

public class Data {
    private static final Logger log = getLogger(Data.class);
    private final Threading threading;
    private final Conf configuration;
    private HikariDataSource dataSource;
    private final Guilds guilds;
    private final Products products;
    private final NexusRest nexus;
    private final Mailings mailings;
    private final KoFiProducts kofi;
    private final Accounts accounts;
    private final AccountLicenses accountLicenses;
    private final LicenseInvites licenseInvites;
    private final KioskProducts kioskProducts;
    private final AccountSessions accountSessions;
    private final RevokedJtis revokedJtis;
    private final DownloadLog downloadLog;
    private final DemoArtifacts demoArtifacts;
    private final InstanceSettingsAccess instanceSettings;
    private final InstanceOperators instanceOperators;
    private final PasswordResetTokens passwordResetTokens;
    private final EmailVerificationTokens emailVerificationTokens;
    private final PasswordHasher passwordHasher;
    private final JwtService jwtService;
    private final DiscordOAuthClient discordOAuthClient;

    /**
     * <p>Wide on purpose, and temporarily. Everything here used to be built by {@code initDao} and
     * handed out through a getter, so the rest of the application reaches its data access by way of
     * this class. As each consumer starts asking for what it needs directly, these fall away.
     */
    @Inject
    public Data(Threading threading, Conf configuration, Guilds guilds, Products products,
                NexusRest nexus, Mailings mailings, KoFiProducts kofi, Accounts accounts,
                AccountLicenses accountLicenses, LicenseInvites licenseInvites,
                KioskProducts kioskProducts, AccountSessions accountSessions, RevokedJtis revokedJtis,
                DownloadLog downloadLog, DemoArtifacts demoArtifacts,
                InstanceSettingsAccess instanceSettings, InstanceOperators instanceOperators,
                PasswordResetTokens passwordResetTokens,
                EmailVerificationTokens emailVerificationTokens, PasswordHasher passwordHasher,
                JwtService jwtService, DiscordOAuthClient discordOAuthClient) {
        this.threading = threading;
        this.configuration = configuration;
        this.guilds = guilds;
        this.products = products;
        this.nexus = nexus;
        this.mailings = mailings;
        this.kofi = kofi;
        this.accounts = accounts;
        this.accountLicenses = accountLicenses;
        this.licenseInvites = licenseInvites;
        this.kioskProducts = kioskProducts;
        this.accountSessions = accountSessions;
        this.revokedJtis = revokedJtis;
        this.downloadLog = downloadLog;
        this.demoArtifacts = demoArtifacts;
        this.instanceSettings = instanceSettings;
        this.instanceOperators = instanceOperators;
        this.passwordResetTokens = passwordResetTokens;
        this.emailVerificationTokens = emailVerificationTokens;
        this.passwordHasher = passwordHasher;
        this.jwtService = jwtService;
        this.discordOAuthClient = discordOAuthClient;
    }

    /** How long to wait before asking the database again. */
    private static final Duration CONNECT_RETRY_DELAY = Duration.ofSeconds(10);

    /**
     * Opens the database and makes it usable, which construction deliberately does not.
     *
     * <p>Waiting for a database, migrating a schema and installing a global query configuration are
     * not things to do while an injector is building an object graph. Everything built above this
     * can be constructed before the database exists; nothing may be <em>used</em> before this has run.
     */
    public void start() throws SQLException, IOException, InterruptedException {
        initConnection();
        configure();
        updateDatabase();
    }

    /**
     * Waits for the database, however long that takes.
     *
     * <p>A deployment routinely starts before its database does, so a refused connection is not a
     * reason to give up - it is a reason to wait. There is no attempt limit for the same reason: an
     * application that exits after five tries only moves the problem to whatever restarts it.
     *
     * <p>Being interrupted is the one way out. That is a shutdown asking the process to stop, and
     * stopping is what it should do rather than going back to sleep.
     *
     * @throws InterruptedException if the wait is interrupted. Thrown rather than swallowed so the
     *                              signal reaches the caller: the sleep clears the thread's
     *                              interrupt flag, so the exception is all that is left of it
     */
    public void initConnection() throws InterruptedException {
        while (true) {
            try {
                dataSource = getConnectionPool();
                return;
            } catch (Exception e) {
                log.error(LogNotify.NOTIFY_ADMIN, "Could not connect to database. Retrying in {}s.",
                        CONNECT_RETRY_DELAY.toSeconds(), e);
            }
            Thread.sleep(CONNECT_RETRY_DELAY.toMillis());
        }
    }

    private void updateDatabase() throws IOException, SQLException {
        var schema = configuration.main().database().schema();
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


    private HikariDataSource getConnectionPool() {
        log.info("Creating connection pool.");
        var data = configuration.main().database();
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

    public LicenseInvites licenseInvites() {
        return licenseInvites;
    }

    public AccountLicenses accountLicenses() {
        return accountLicenses;
    }

    public KioskProducts kioskProducts() {
        return kioskProducts;
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

    public DemoArtifacts demoArtifacts() {
        return demoArtifacts;
    }

    public InstanceSettingsAccess instanceSettings() {
        return instanceSettings;
    }

    public InstanceOperators instanceOperators() {
        return instanceOperators;
    }

    public PasswordResetTokens passwordResetTokens() {
        return passwordResetTokens;
    }

    public EmailVerificationTokens emailVerificationTokens() {
        return emailVerificationTokens;
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
