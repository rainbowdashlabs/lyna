package de.chojo.lyna.core;

import com.zaxxer.hikari.HikariDataSource;
import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.configuration.elements.Nexus;
import de.chojo.lyna.data.StaticQueryAdapter;
import de.chojo.lyna.data.access.Guilds;
import de.chojo.nexus.NexusRest;
import de.chojo.sadu.databases.PostgreSql;
import de.chojo.sadu.datasource.DataSourceCreator;
import de.chojo.sadu.updater.QueryReplacement;
import de.chojo.sadu.updater.SqlUpdater;
import de.chojo.sadu.wrapper.QueryBuilderConfig;
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
    private NexusRest nexus;

    private Data(Threading threading, Configuration<ConfigFile> configuration) {
        this.threading = threading;
        this.configuration = configuration;
        Nexus nexus = configuration.config().nexus();
        this.nexus = NexusRest.builder(nexus.host())
                .setPasswordAuth(nexus.username(), nexus.password())
                .build();
    }

    public static Data create(Threading threading, Configuration<ConfigFile> configuration) throws SQLException, IOException, InterruptedException {
        var data = new Data(threading, configuration);
        data.init();
        return data;
    }

    public void init() throws SQLException, IOException, InterruptedException {
        configure();
        initConnection();
        updateDatabase();
        initSaduAdapter();
        initDao();
    }

    private void initSaduAdapter() {
        StaticQueryAdapter.start(dataSource);
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
        QueryBuilderConfig.setDefault(QueryBuilderConfig.builder()
                .withExceptionHandler(err -> logger.error(LogNotify.NOTIFY_ADMIN, "An error occured during a database request", err))
                .withExecutor(threading.botWorker())
                .build());
    }

    private void initDao() {
        log.info("Creating DAOs");
        guilds = new Guilds();
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
}
