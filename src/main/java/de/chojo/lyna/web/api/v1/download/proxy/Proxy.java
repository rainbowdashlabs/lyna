package de.chojo.lyna.web.api.v1.download.proxy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import de.chojo.jdautil.util.SnowflakeCreator;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.util.JarUtil;
import de.chojo.lyna.web.api.v1.download.Download;
import io.javalin.http.ContentType;
import io.javalin.http.HttpStatus;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

public class Proxy {
    private static final Logger log = getLogger(Proxy.class);
    private final Download download;
    private final Cache<String, AssetDownload> tokens = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
    @Language("HTML")
    private final String shareHtml = """
            <!DOCTYPE html>
            <html lang="en">
            <head>
                <meta charset="UTF-8" />
                <meta name="viewport" content="width=device-width, initial-scale=1.0" />
                <meta name="author" content="Chojo">
                <meta name="description" content="I know sharing is caring, but it would be nice if your links stay your links ^-^">
                        
                <meta property="og:title" content="I said do not share c:">
                <meta property="og:description" content="I know sharing is caring, but it would be nice if your links stay your links ^-^">
                <meta property="og:image" content="https://cdn.discordapp.com/emojis/940946164646293514.webp">
                <meta property="og:url" content="https://cdn.discordapp.com/emojis/940946164646293514.webp">
                        
                <meta name="twitter:card" content="summary_large_image">
                <meta name="theme-color" content="#ff0faf">
                        
                <link rel="icon" href="{{ favicon }}">
                        
                <title>I said do not share c:</title>
            <body>
            </body>
            </html>
                        
            """;
    private final SnowflakeCreator snowflakeCreator = SnowflakeCreator.builder().build();

    public Proxy(Download download) {
        this.download = download;
    }

    public void init() {
        path("proxy", () -> {
            get(ctx -> {
                String token = ctx.queryParam("token");
                if (token == null) {
                    ctx.status(HttpStatus.BAD_REQUEST);
                    ctx.result("Missing token");
                    return;
                }
                AssetDownload download = tokens.getIfPresent(token);
                if (download == null) {
                    ctx.status(HttpStatus.BAD_REQUEST);
                    ctx.result("Invalid token.");
                    return;
                }

                tokens.invalidate(token);
                String agent = ctx.header("User-Agent");
                if (agent != null && agent.toLowerCase(Locale.ROOT).contains("discordbot")) {
                    ctx.status(HttpStatus.OK)
                            .contentType(ContentType.TEXT_HTML)
                            .result(shareHtml);
                    return;
                }

                var asset = this.download.v1().api().nexus().v1().assets().get(download.assetId()).complete();
                String filename = "%s-%s.%s".formatted(asset.maven2().artifactId(), asset.maven2().version(), asset.maven2().extension());

                download.postDownload().run();

                var complete = asset.downloadStream().complete();
                ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename))
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(ContentType.APPLICATION_OCTET_STREAM)
                        .status(HttpStatus.OK);

                if ("true".equalsIgnoreCase(System.getProperty("bot.jarsigning.skip"))) {
                    ctx.result(complete);
                    return;
                }

                Map<String, String> replacements = Map.of(
                        "%%__USER__%%", download.userId(),
                        "%%__RESOURCE__%%", download.assetId(),
                        "%%__NONCE__%%", snowflakeCreator.nextString()
                );

                byte[] replacedJarFile;
                try {
                    replacedJarFile = JarUtil.replaceStringsInJar(complete, replacements);
                } catch (Exception e) {
                    log.error(LogNotify.NOTIFY_ADMIN, "Could not replace strings in jar.", e);
                    try (var in = asset.downloadStream().complete()) {
                        replacedJarFile = in.readAllBytes();
                    }
                }
                ctx.result(replacedJarFile);
            });
        });
    }

    public String registerAsset(AssetDownload assetDownload) {
        var hashCode = Hashing.sha512().hashString(System.nanoTime() + assetDownload.assetId() + System.nanoTime(), StandardCharsets.UTF_8).toString();
        tokens.put(hashCode, assetDownload);
        return "%s/api/v1/download/proxy?token=%s".formatted(download.v1().api().configuration().config().api().url(), hashCode);
    }
}
