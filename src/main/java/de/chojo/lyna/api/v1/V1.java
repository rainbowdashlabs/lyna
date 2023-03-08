package de.chojo.lyna.api.v1;

import de.chojo.lyna.api.Api;
import de.chojo.lyna.api.v1.download.Download;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class V1 {
    private final Download download;
    private final Api api;

    public V1(Api api) {
        this.api = api;
        download = new Download(this);
    }

    public void init() {
        path("v1", () -> {
            download.init();
        });
    }

    public Api api() {
        return api;
    }

    public Download download() {
        return download;
    }
}
