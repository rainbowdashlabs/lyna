package de.chojo.lyna.api.v1;

import de.chojo.lyna.api.Api;
import de.chojo.lyna.api.v1.download.Download;
import de.chojo.lyna.api.v1.kofi.KoFiApi;
import de.chojo.lyna.api.v1.update.Update;
import de.chojo.lyna.data.access.KoFiProducts;
import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.mail.MailingService;

import static io.javalin.apibuilder.ApiBuilder.path;

public class V1 {
    private final Download download;
    private final Update update;
    private final Api api;
    private final KoFiProducts koFiProducts;
    private final KoFiApi kofi;

    public V1(Api api, Products products, MailingService mailingService, KoFiProducts koFiProducts) {
        this.api = api;
        this.koFiProducts = koFiProducts;
        download = new Download(this);
        update = new Update(this, products);
        kofi = new KoFiApi(this, koFiProducts, mailingService);
    }

    public void init() {
        path("v1", () -> {
            download.init();
            update.init();
            kofi.init();
        });
    }

    public Api api() {
        return api;
    }

    public Download download() {
        return download;
    }
}
