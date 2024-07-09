package de.chojo.lyna.web.downloads;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import io.javalin.http.ContentType;

import java.io.IOException;

import static io.javalin.apibuilder.ApiBuilder.get;

public class Download {
    private String downloadJs = null;
    private final Configuration<ConfigFile> configuration;

    public Download(Configuration<ConfigFile> configuration) {
        this.configuration = configuration;
    }

    public void init() {
        try (var in = getClass().getClassLoader().getResourceAsStream("web/download.js")) {
            downloadJs = new String(in.readAllBytes());
            downloadJs = downloadJs.replace("{{ HOST }}", configuration.config().api().url());
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        get("download.js", ctx -> {
            ctx.result(downloadJs);
            ctx.contentType(ContentType.APPLICATION_JS);
        });
    }
}
