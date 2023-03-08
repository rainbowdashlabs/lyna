package de.chojo.lyna;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Bot;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.core.Web;

import java.io.IOException;
import java.sql.SQLException;

public class Lyna {
    private static Lyna instance;

    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        instance = new Lyna();
        instance.init();
    }

    private void init() throws SQLException, IOException, InterruptedException {
        Configuration<ConfigFile> configuration = Configuration.create(new ConfigFile());
        var threading = new Threading();
        Data data = Data.create(threading, configuration);
        Web web = Web.create(configuration, data);
        Bot.create(data, threading, configuration,web);
    }
}
