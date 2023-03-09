package de.chojo.lyna.api;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.api.v1.V1;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.nexus.NexusRest;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import org.slf4j.Logger;

import java.util.stream.Collectors;

import static io.javalin.apibuilder.ApiBuilder.after;
import static io.javalin.apibuilder.ApiBuilder.before;
import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class Api {
    private final Javalin javalin;
    private final Configuration<ConfigFile> configuration;
    private final NexusRest nexus;
    private final V1 v1;

    private static final Logger log = getLogger(Api.class);

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
            before(ctx -> {
                log.trace("Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + ctx.url(),
                        ctx.queryString(),
                        ctx.headerMap().entrySet().stream().map(h -> "   " + h.getKey() + ": " + h.getValue())
                                .collect(Collectors.joining("\n")),
                        ctx.body().substring(0, Math.min(ctx.body().length(), 180)));
                ctx.header("Access-Control-Allow-Origin", "*");
                ctx.header("Access-Control-Allow-Headers", "*");
                ctx.header("Content-Security-Policy", "default-src 'self'; script-src 'none'; frame-src 'none'; style-src 'self'; img-src eldoria.de discordapp.com; media-src 'none'");
            });

            after(ctx -> {
                log.trace("Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                        ctx.method() + " " + ctx.url(),
                        ctx.queryString(),
                        ctx.status(),
                        ctx.res.getHeaderNames().stream().map(h -> "   " + h + ": " + ctx.res.getHeader(h))
                                .collect(Collectors.joining("\n")),
                        ContentType.OCTET_STREAM.equals(ctx.contentType()) ? "Bytes" : ctx.body().substring(0, Math.min(ctx.body().length(), 180)));
            });

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
