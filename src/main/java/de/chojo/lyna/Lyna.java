package de.chojo.lyna;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Bot;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.core.Web;
import de.chojo.lyna.mail.MailingService;

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
        MailingService mailingService = MailingService.create(threading, data, configuration);
        Web web = Web.create(configuration, data, mailingService);
        Bot bot = Bot.create(data, threading, configuration, web, mailingService);
        data.inject(bot);
    }
}
