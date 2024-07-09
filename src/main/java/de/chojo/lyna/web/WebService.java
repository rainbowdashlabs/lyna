package de.chojo.lyna.web;

import de.chojo.jdautil.configuratino.Configuration;
import de.chojo.lyna.commands.kofi.handler.Link;
import de.chojo.lyna.configuration.ConfigFile;
import de.chojo.lyna.core.Data;
import de.chojo.lyna.mail.MailingService;
import de.chojo.lyna.web.api.Api;
import de.chojo.lyna.web.downloads.Download;
import de.chojo.nexus.NexusRest;
import io.javalin.Javalin;
import io.javalin.core.util.Headers;
import io.javalin.http.ContentType;
import io.javalin.http.staticfiles.Location;
import org.slf4j.Logger;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

import static io.javalin.apibuilder.ApiBuilder.after;
import static io.javalin.apibuilder.ApiBuilder.before;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class WebService {
    private final Javalin javalin;
    private final Configuration<ConfigFile> configuration;
    private final NexusRest nexus;
    private final Api api;
    private static final Logger log = getLogger(WebService.class);
    private final Download download;

    private WebService(Javalin javalin, Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        this.javalin = javalin;
        this.configuration = configuration;
        this.nexus = data.nexus();
        api = new Api(this, configuration, data, mailingService);
        download = new Download(configuration);
    }

    public static WebService create(Configuration<ConfigFile> configuration, Data data, MailingService mailingService) {
        Javalin javalin = Javalin.create(config -> {
            config.addStaticFiles(staticFiles -> {
                staticFiles.hostedPath = "/";
                staticFiles.directory = "/web";
                staticFiles.location = Location.CLASSPATH;
                staticFiles.precompress = true;
            });
            config.globalHeaders(() -> {
                Headers headers = new Headers();
                var cspList = List.of("default-src 'self' {{ HOST }}",
                        "script-src 'self' {{ HOST }} *.fontawesome.com",
                        "frame-src 'none'",
                        "connect-src {{ HOST }} *.fontawesome.com",
                        "style-src 'self' {{ HOST }} fonts.googleapis.com 'unsafe-inline'", // unsafe inline for fontawesome
                        "img-src {{ HOST }} discordapp.com",
                        "media-src 'none'",
                        "font-src  fonts.gstatic.com *.fontawesome.com");
                var csp =  String.join("; ", cspList);
                csp = csp.replace("{{ HOST }}", configuration.config().api().hostname());
                headers.contentSecurityPolicy(csp);
                headers.getHeaders().put("Access-Control-Allow-Origin", "*");
                headers.getHeaders().put("Access-Control-Allow-Headers", "*");
                return headers;
            });
        });
        WebService web = new WebService(javalin, configuration, data, mailingService);
        web.init();
        return web;
    }


    public void init() {
        javalin.routes(() -> {
            before(ctx -> {
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
                        ctx.res.getHeaderNames().stream().map(h -> "   " + h + ": " + ctx.res.getHeader(h))
                                .collect(Collectors.joining("\n")),
                        ContentType.OCTET_STREAM.equals(ctx.contentType()) ? "Bytes"
                                : Objects.requireNonNullElse(ctx.resultString(), "")
                                .substring(0, Math.min(
                                        Objects.requireNonNullElse(ctx.resultString(), "").length(), 180)));
            });

            api.init();
            download.init();

        });
        javalin.start(configuration.config().api().host(), configuration.config().api().port());
    }

    public Api api() {
        return api;
    }
}
