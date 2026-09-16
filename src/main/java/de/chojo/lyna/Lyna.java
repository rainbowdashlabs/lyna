package de.chojo.lyna;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.core.Bot;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.core.Threading;
import de.chojo.lyna.core.Web;
import de.chojo.lyna.demo.DemoSchedule;
import de.chojo.lyna.inject.LynaModule;
import de.chojo.lyna.demo.DemoService;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.gateway.JdaGateway;
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
        Conf configuration = new Conf();
        Injector injector = Guice.createInjector(new LynaModule(configuration));
        var threading = injector.getInstance(Threading.class);
        Data data = injector.getInstance(Data.class);
        data.start();
        MailingService mailingService = MailingService.create(threading, data, configuration);
        DemoService demoService = new DemoService(data, configuration);
        Web web = Web.create(configuration, data, mailingService, demoService);
        Bot bot = Bot.create(data, threading, configuration, web, mailingService);
        Gateway gateway = bot.shardManager() == null
                ? Gateway.NONE
                : new JdaGateway(bot::shardManager);
        data.inject(gateway, web.webService().api());
        demoService.gateway(gateway);
        if (gateway.connected()) {
            DemoSchedule.start(threading, demoService, configuration);
        }
    }
}
