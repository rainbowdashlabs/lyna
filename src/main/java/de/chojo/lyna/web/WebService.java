package de.chojo.lyna.web;

import com.google.common.collect.Streams;
import de.chojo.jdautil.configuration.Configuration;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.api.Api;
import io.javalin.Javalin;
import io.javalin.http.ContentType;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;

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
        javalin = Javalin.create(config -> {
            config.staticFiles.add(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.precompress = true;
            });
            config.useVirtualThreads = true;
            config.router.apiBuilder(this::routes);
        });


        javalin.start(configuration.config().api().host(), configuration.config().api().port());
    }

    private void routes() {
        before(ctx -> {
            var cspList = List.of("default-src 'self' {{ HOST }}",
                    "script-src 'self' {{ HOST }} *.fontawesome.com",
                    "frame-src 'none'",
                    "connect-src {{ HOST }} *.fontawesome.com",
                    "style-src 'self' {{ HOST }} fonts.googleapis.com 'unsafe-inline'", // unsafe inline for fontawesome
                    "img-src {{ HOST }} discordapp.com",
                    "media-src 'none'",
                    "font-src  fonts.gstatic.com *.fontawesome.com");
            var csp = String.join("; ", cspList);
            csp = csp.replace("{{ HOST }}", configuration.config().api().hostname());
            ctx.header("Content-Security-Policy", csp);
            ctx.header("Access-Control-Allow-Origin", "*");
            ctx.header("Access-Control-Allow-Headers", "*");


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
                    Streams.stream(ctx.req().getHeaderNames().asIterator()).map(h -> "   " + h + ": " + ctx.res().getHeader(h))
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
