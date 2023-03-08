package de.chojo.lyna.api;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.api.v1.V1;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.nexus.NexusRest;
import io.javalin.Javalin;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Api {
    private final Javalin javalin;
    private final Configuration<ConfigFile> configuration;
    private final NexusRest nexus;
    private final V1 v1;

    private Api(Javalin javalin, Configuration<ConfigFile> configuration, NexusRest nexus) {
        this.javalin = javalin;
        this.configuration = configuration;
        this.nexus = nexus;
        v1 = new V1(this);
    }

    public static Api create(Configuration<ConfigFile> configuration, NexusRest nexus) {
        Javalin javalin = Javalin.create();
        Api api = new Api(javalin, configuration, nexus);
        api.init();
        return api;
    }

    private void init() {
        javalin.routes(() -> {
            path("api", () -> {
                v1.init();
                get(ctx -> {
                    ctx.result("Henlo");
                });
            });
        });
        javalin.start(configuration.config().api().host(), configuration.config().api().port());
    }

    public Configuration<ConfigFile> configuration() {
        return configuration;
    }

    public NexusRest nexus() {
        return nexus;
    }

    public V1 v1() {
        return v1;
    }
}
