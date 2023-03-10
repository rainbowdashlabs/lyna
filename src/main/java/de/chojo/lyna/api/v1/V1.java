package de.chojo.lyna.api.v1;

import de.chojo.lyna.api.Api;
import de.chojo.lyna.api.v1.download.Download;
import de.chojo.lyna.api.v1.update.Update;
import de.chojo.lyna.data.access.Products;

import static io.javalin.apibuilder.ApiBuilder.path;

public class V1 {
    private final Download download;
    private final Update update;
    private final Api api;

    public V1(Api api, Products products) {
        this.api = api;
        download = new Download(this);
        update = new Update(this, products);
    }

    public void init() {
        path("v1", () -> {
            download.init();
            update.init();
        });
    }

    public Api api() {
        return api;
    }

    public Download download() {
        return download;
    }
}
