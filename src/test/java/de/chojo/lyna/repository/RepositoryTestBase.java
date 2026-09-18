/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.lyna.TestContainers;
import de.chojo.lyna.data.access.DemoArtifacts;
import de.chojo.lyna.data.access.DownloadLog;
import de.chojo.lyna.data.access.InstanceOperators;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.access.KioskProducts;
import de.chojo.lyna.data.access.LicenseInvites;
import de.chojo.lyna.feature.account.repository.AccountEmailRepository;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;
import de.chojo.lyna.feature.account.repository.EmailVerificationTokenRepository;
import de.chojo.lyna.feature.account.repository.PasswordResetTokenRepository;
import de.chojo.lyna.feature.account.repository.RevokedJtiRepository;
import de.chojo.lyna.feature.account.service.AccountEmailService;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.account.service.AccountService;
import de.chojo.lyna.feature.account.service.PurchaseCollectionService;
import de.chojo.lyna.feature.account.service.UsernameService;
import de.chojo.lyna.feature.license.repository.LicenseRepository;
import de.chojo.lyna.feature.license.service.LicenseService;
import de.chojo.lyna.feature.license.service.LicenseSharingService;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.mapper.RowMapperRegistry;
import de.chojo.sadu.postgresql.databases.PostgreSql;
import de.chojo.sadu.postgresql.mapper.PostgresqlMapper;
import de.chojo.sadu.queries.configuration.QueryConfiguration;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import org.junit.jupiter.api.BeforeAll;
import org.testcontainers.containers.PostgreSQLContainer;

import java.sql.SQLException;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * The database a repository test runs against, and the access objects that read it.
 *
 * <p>Extending this gives a schema of this class's own on a PostgreSQL container shared by every
 * repository test class in the same Gradle fork, migrated by the same {@link SqlUpdater} run the
 * application performs at startup. Tests therefore see the real schema rather than a hand-written
 * approximation of it, and a migration that does not apply fails them.
 */
public abstract class RepositoryTestBase {
    private static final AtomicInteger SCHEMA_COUNTER = new AtomicInteger(0);

    /**
     * A single PostgreSQL container per JVM (Gradle test fork), started lazily on the first test
     * class's {@link #setupDatabase()} and shared by every repository test class in that fork; each
     * class isolates its data in its own schema. Sharing one container - instead of letting the
     * {@code @Testcontainers} lifecycle start and stop one per test class - removes the container
     * start/stop churn under parallel forks that lets rootless Docker occasionally hand two
     * concurrently-starting containers the same host port. {@code withStartupAttempts} self-heals
     * the rare remaining collision, and the container is reaped when the fork's JVM exits.
     *
     * <p>Startup is deliberately kept out of a static initialiser: a transient Docker failure there
     * would poison this class for the whole fork ({@code NoClassDefFoundError} on every later
     * class). From {@code @BeforeAll} a failure fails only the current class and the next one
     * retries the start.
     */
    static final PostgreSQLContainer<?> PG = new PostgreSQLContainer<>("postgres:17")
            .withDatabaseName("lyna_test")
            .withUsername("test")
            .withPassword("test")
            .withStartupAttempts(8);

    protected static HikariDataSource dataSource;
    protected static String schemaName;

    protected static AccountRepository accounts;
    protected static AccountLicenseRepository accountLicenses;
    protected static AccountEmailRepository accountEmails;
    protected static LicenseInvites licenseInvites;
    protected static AccountSessionRepository accountSessions;
    protected static RevokedJtiRepository revokedJtis;
    protected static DownloadLog downloadLog;
    protected static DemoArtifacts demoArtifacts;
    protected static InstanceSettingsAccess instanceSettings;
    protected static InstanceOperators instanceOperators;
    protected static KioskProducts kioskProducts;
    protected static PasswordResetTokenRepository passwordResetTokens;
    protected static EmailVerificationTokenRepository emailVerificationTokens;

    protected static AccountService accountService;
    protected static AccountEmailService accountEmailService;
    protected static PurchaseCollectionService purchaseCollection;
    protected static UsernameService usernameService;
    protected static AccountLinkService accountLinks;
    protected static LicenseRepository licenseRepository;
    protected static LicenseSharingService licenseSharing;
    protected static LicenseService licenseService;

    @BeforeAll
    static void setupDatabase() throws Exception {
        TestContainers.startExclusively(PG);
        String schema = "lyna_t" + SCHEMA_COUNTER.incrementAndGet();

        dataSource = DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config.host(PG.getHost())
                        .port(PG.getFirstMappedPort())
                        .user(PG.getUsername())
                        .password(PG.getPassword())
                        .database(PG.getDatabaseName()))
                .create()
                .withMaximumPoolSize(5)
                .forSchema(schema)
                .build();
        schemaName = schema;

        SqlUpdater.builder(dataSource, PostgreSql.get())
                .setReplacements(new QueryReplacement("lyna", schema))
                .setVersionTable(schema + ".lyna_version")
                .setSchemas(schema)
                .execute();

        QueryConfiguration.setDefault(QueryConfiguration.builder(dataSource)
                .setThrowExceptions(true)
                .setRowMapperRegistry(new RowMapperRegistry().register(PostgresqlMapper.getDefaultMapper()))
                .build());

        accounts = new AccountRepository();
        accountLicenses = new AccountLicenseRepository();
        accountEmails = new AccountEmailRepository();
        licenseInvites = new LicenseInvites();
        accountSessions = new AccountSessionRepository();
        revokedJtis = new RevokedJtiRepository();
        downloadLog = new DownloadLog();
        demoArtifacts = new DemoArtifacts();
        instanceSettings = new InstanceSettingsAccess();
        instanceOperators = new InstanceOperators();
        kioskProducts = new KioskProducts();
        passwordResetTokens = new PasswordResetTokenRepository();
        emailVerificationTokens = new EmailVerificationTokenRepository();

        accountService = new AccountService(accounts, accountEmails);
        purchaseCollection = new PurchaseCollectionService(accountEmails, accountLicenses, licenseInvites);
        accountEmailService = new AccountEmailService(accountEmails, purchaseCollection);
        usernameService = new UsernameService(accounts);
        accountLinks = new AccountLinkService(accounts, usernameService);
        licenseRepository = new LicenseRepository();
        licenseSharing = new LicenseSharingService(licenseRepository, accountLinks);
        licenseService = new LicenseService(licenseRepository, accountLinks, licenseSharing);
    }

    /**
     * Empties the tables a test may have written to, so the class's own schema starts each test the
     * way it found it. Deleting rather than truncating keeps the sequences moving, which is what
     * stops a test from passing because two entities happened to be handed the same id.
     *
     * @param tables the unqualified table names, in an order no foreign key objects to
     */
    /**
     * @param table a table in the test schema
     * @return how many rows it holds
     */
    protected static int countRows(String table) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement();
                var rows = statement.executeQuery("SELECT count(*) FROM %s.%s".formatted(schemaName, table))) {
            rows.next();
            return rows.getInt(1);
        }
    }

    protected static void clear(String... tables) throws SQLException {
        try (var connection = dataSource.getConnection();
                var statement = connection.createStatement()) {
            for (String table : tables) {
                statement.execute("DELETE FROM %s.%s".formatted(schemaName, table));
            }
        }
    }
}
