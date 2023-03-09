package de.chojo.lyna.api.v1.download;

import de.chojo.lyna.api.v1.V1;
import de.chojo.lyna.api.v1.download.proxy.Proxy;

import static io.javalin.apibuilder.ApiBuilder.path;

public class Download {
    private final V1 v1;
    Proxy proxy;

    public Download(V1 v1) {
        this.v1 = v1;
        this.proxy = new Proxy(this);
    }

    public void init() {
        path("download", () -> {
            proxy.init();
        });
    }

    public Proxy proxy() {
        return proxy;
    }

    public V1 v1() {
        return v1;
    }
}
