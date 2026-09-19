/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna;

import com.google.inject.Guice;
import com.google.inject.Injector;
import de.chojo.lyna.configuration.Conf;
import de.chojo.lyna.core.Bot;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.demo.DemoSchedule;
import de.chojo.lyna.inject.LynaModule;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.WebService;

import java.io.IOException;
import java.sql.SQLException;

public class Lyna {
    private static Lyna instance;

    public static void main(String[] args) throws SQLException, IOException, InterruptedException {
        System.setProperty("java.awt.headless", "true");
        instance = new Lyna();
        instance.init();
    }

    /**
     * Builds the application and starts the parts that start.
     *
     * <p>What used to be a fixed order of {@code create(...)} calls ending in a back-patch is now a
     * graph the injector assembles. What remains here is the order things have to be <em>started</em>
     * in, which is a genuine sequence: the database before anything reads it, the gateway last
     * because everything else answers without it.
     */
    private void init() throws SQLException, IOException, InterruptedException {
        Injector injector = Guice.createInjector(new LynaModule(new Conf()));

        injector.getInstance(Data.class).start();
        injector.getInstance(MailingService.class).start();
        injector.getInstance(WebService.class).init();
        injector.getInstance(Bot.class).start();
        injector.getInstance(DemoSchedule.class).start();
    }
}
