/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1;

import com.google.inject.Inject;
import de.chojo.lyna.web.api.v1.demo.DemoApi;
import de.chojo.lyna.web.api.v1.download.Download;
import de.chojo.lyna.web.api.v1.kofi.KoFiApi;
import de.chojo.lyna.web.api.v1.products.Products;
import de.chojo.lyna.web.api.v1.products.Wizard;
import de.chojo.lyna.web.api.v1.releases.Releases;
import de.chojo.lyna.web.api.v1.update.Update;

import static io.javalin.apibuilder.ApiBuilder.path;

/**
 * Where version one of the API is mounted.
 *
 * <p>Holds its parts to mount them and for nothing else. They used to be built here and handed a
 * reference back to this, which is how a class reached the configuration - by walking up to whoever
 * owned it. They ask for what they need now, so this is a list of routes rather than a place things
 * are fetched from.
 */
public class V1 {
    private final Download download;
    private final Update update;
    private final KoFiApi kofi;
    private final Products products;
    private final Releases releases;
    private final Wizard wizard;
    private final DemoApi demoApi;

    @Inject
    public V1(
            Download download,
            Update update,
            KoFiApi kofi,
            Products products,
            Releases releases,
            Wizard wizard,
            DemoApi demoApi) {
        this.download = download;
        this.update = update;
        this.kofi = kofi;
        this.products = products;
        this.releases = releases;
        this.wizard = wizard;
        this.demoApi = demoApi;
    }

    public void init() {
        path("v1", () -> {
            download.init();
            update.init();
            kofi.init();
            products.init();
            releases.init();
            wizard.init();
            demoApi.init();
        });
    }
}
