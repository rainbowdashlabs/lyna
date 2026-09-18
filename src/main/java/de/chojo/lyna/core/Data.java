/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.core;

import com.google.inject.Inject;
import com.zaxxer.hikari.HikariDataSource;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.Conf;
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

/**
 * Opens the database and keeps it open.
 *
 * <p>Holds the pool and the migration and hands nothing out. It used to be the directory the whole
 * application reached its data access through - a class took {@code Data} and asked it for the six
 * things it wanted - so it carried twenty collaborators it made no use of itself. They are built by
 * the module now, and asked for by name.
 */
public class Data {
    private static final Logger log = getLogger(Data.class);
    private final Threading threading;
    private final Conf configuration;
    private HikariDataSource dataSource;

    @Inject
    public Data(Threading threading, Conf configuration) {
        this.threading = threading;
        this.configuration = configuration;
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
                log.error(
                        LogNotify.NOTIFY_ADMIN,
                        "Could not connect to database. Retrying in {}s.",
                        CONNECT_RETRY_DELAY.toSeconds(),
                        e);
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
                .setExceptionHandler(
                        err -> logger.error(LogNotify.NOTIFY_ADMIN, "An error occurred during a database request", err))
                .build());
    }

    private HikariDataSource getConnectionPool() {
        log.info("Creating connection pool.");
        var data = configuration.main().database();
        return DataSourceCreator.create(PostgreSql.get())
                .configure(config -> config.host(data.host())
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
}
