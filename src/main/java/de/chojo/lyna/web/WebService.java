package de.chojo.lyna.web;

import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.api.Api;
import io.javalin.Javalin;
import io.javalin.http.HttpStatus;
import io.javalin.http.ContentType;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.javalin.apibuilder.ApiBuilder.after;
import static io.javalin.apibuilder.ApiBuilder.before;
import static org.slf4j.LoggerFactory.getLogger;

public class WebService {
    private final Configuration<ConfigFile> configuration;
    private final Api api;
    private static final Logger log = getLogger(WebService.class);
    private Javalin javalin;

    private WebService(Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        this.configuration = configuration;
        api = new Api(this, configuration, data, mailingService);
    }

    public static WebService create(Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        WebService web = new WebService(configuration, data, mailingService);
        web.init();
        return web;
    }


    public void init() {
        var apiConfig = configuration.config().api();
        javalin = Javalin.create(config -> {
            if (apiConfig.staticUi()) {
                config.staticFiles.add(staticFiles -> {
                    staticFiles.hostedPath = "/";
                    staticFiles.directory = "/web";
                    staticFiles.location = Location.CLASSPATH;
                    staticFiles.precompress = false;
                });
            }
            config.useVirtualThreads = true;
            config.router.apiBuilder(this::routes);
        });


        javalin.start(apiConfig.host(), apiConfig.port());
    }

    private void routes() {
        var apiConfig = configuration.config().api();
        var imgSrcHosts = new ArrayList<String>();
        imgSrcHosts.add("{{ HOST }}");
        imgSrcHosts.add("discordapp.com");
        imgSrcHosts.add("data:");
        imgSrcHosts.addAll(apiConfig.iconHosts());

        before(ctx -> {
            var cspList = List.of("default-src 'self' {{ HOST }}",
                    "script-src 'self' {{ HOST }} *.fontawesome.com",
                    "frame-src 'none'",
                    "connect-src {{ HOST }} *.fontawesome.com",
                    "style-src 'self' {{ HOST }} fonts.googleapis.com 'unsafe-inline'", // unsafe inline for fontawesome
                    "img-src " + String.join(" ", imgSrcHosts),
                    "media-src 'none'",
                    "font-src  fonts.gstatic.com *.fontawesome.com");
            var csp = String.join("; ", cspList);
            csp = csp.replace("{{ HOST }}", apiConfig.hostname());
            ctx.header("Content-Security-Policy", csp);

            var origin = ctx.header("Origin");
            if (origin != null && apiConfig.allowedOrigins().contains(origin)) {
                ctx.header("Access-Control-Allow-Origin", origin);
                ctx.header("Vary", "Origin");
                ctx.header("Access-Control-Allow-Methods", "GET, POST, PUT, PATCH, DELETE, OPTIONS");
                ctx.header("Access-Control-Allow-Headers", "Authorization, Content-Type");
                ctx.header("Access-Control-Max-Age", "600");
                if ("OPTIONS".equalsIgnoreCase(ctx.method().name())) {
                    ctx.status(204);
                    return;
                }
            }

            log.trace("Received request on route: {} {}\nHeaders:\n{}\nBody:\n{}",
                    ctx.method() + " " + ctx.url(),
                    ctx.queryString(),
                    ctx.headerMap().entrySet().stream().map(h -> "   " + h.getKey() + ": " + h.getValue())
                            .collect(Collectors.joining("\n")),
                    ctx.body().substring(0, Math.min(ctx.body().length(), 180)));
        });

        after(ctx -> {
            log.trace("Answered request on route: {} {}\nStatus: {}\nHeaders:\n{}\nBody:\n{}",
                    ctx.method() + " " + ctx.url(),
                    ctx.queryString(),
                    ctx.status(),
                    ctx.res().getHeaderNames().stream().map(h -> "   " + h + ": " + ctx.res().getHeader(h))
                            .collect(Collectors.joining("\n")),
                    ContentType.OCTET_STREAM.equals(ctx.contentType()) ? "Bytes"
                            : Objects.requireNonNullElse(ctx.result(), "")
                            .substring(0, Math.min(
                                    Objects.requireNonNullElse(ctx.result(), "").length(), 180)));
        });

        api.init();
    }

    public Api api() {
        return api;
    }
}
