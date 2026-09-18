package de.chojo.lyna.web.api.v1.download;

import com.google.inject.Inject;
import de.chojo.lyna.web.api.v1.download.direct.Direct;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;

import static io.javalin.apibuilder.ApiBuilder.path;

public class Download {
    private final Direct direct;
    private final Proxy proxy;

    @Inject
    public Download(Proxy proxy, Direct direct) {
        this.proxy = proxy;
        this.direct = direct;
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

}
