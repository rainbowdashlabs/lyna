package de.chojo.lyna.web.api.v1.download;

import de.chojo.lyna.data.access.Products;
import de.chojo.lyna.web.api.v1.V1;
import de.chojo.lyna.web.api.v1.download.direct.Direct;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;

import static io.javalin.apibuilder.ApiBuilder.path;

public class Download {
    private final V1 v1;
    private final Direct direct;
    Proxy proxy;

    public Download(V1 v1, Products products) {
        this.v1 = v1;
        this.proxy = new Proxy(this);
        direct = new Direct(this, products);
    }

    public void init() {
        path("download", () -> {
            proxy.init();
            direct.init();
        });
    }

    public Proxy proxy() {
        return proxy;
    }

    public V1 v1() {
        return v1;
    }
}
