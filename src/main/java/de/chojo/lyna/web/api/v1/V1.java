package de.chojo.lyna.web.api.v1;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.web.api.Api;
import de.chojo.lyna.web.api.v1.download.Download;
import de.chojo.lyna.web.api.v1.kofi.KoFiApi;
import de.chojo.lyna.web.api.v1.releases.Releases;
import de.chojo.lyna.web.api.v1.update.Update;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.mail.MailingService;

import static io.javalin.apibuilder.ApiBuilder.path;

public class V1 {
    private final Download download;
    private final Update update;
    private final Api api;
    private final KoFiApi kofi;
    private final de.chojo.lyna.web.api.v1.products.Products products;
    private final Releases releases;

    public V1(Api api, Products products, MailingService mailingService, KoFiProducts koFiProducts) {
        this.api = api;
        download = new Download(this, products);
        update = new Update(this, products);
        kofi = new KoFiApi(this, koFiProducts, mailingService);
        this.products = new de.chojo.lyna.web.api.v1.products.Products(this, products);
        releases = new Releases(this, products);
    }

    public void init() {
        path("v1", () -> {
            download.init();
            update.init();
            kofi.init();
            products.init();
            releases.init();
        });
    }

    public Api api() {
        return api;
    }

    public Download download() {
        return download;
    }

    public Configuration<ConfigFile> configuration() {
        return api.configuration();
    }
}
