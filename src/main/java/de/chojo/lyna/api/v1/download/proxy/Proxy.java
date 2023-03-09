package de.chojo.lyna.api.v1.download.proxy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import de.chojo.lyna.api.v1.download.Download;
import io.javalin.http.ContentType;
import io.javalin.http.HttpCode;

import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Proxy {
    private final Download download;
    private final Cache<String, String> tokens = CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();

    public Proxy(Download download) {
        this.download = download;
    }

    public void init() {
        path("proxy", () -> {
            get(ctx -> {
                String token = ctx.queryParam("token");
                if (token == null) {
                    ctx.status(HttpCode.BAD_REQUEST);
                    ctx.result("Missing token");
                    return;
                }
                String assetId = tokens.getIfPresent(token);
                if (assetId == null) {
                    ctx.status(HttpCode.BAD_REQUEST);
                    ctx.result("Invalid token.");
                    return;
                }

                tokens.invalidate(token);

                var asset = download.v1().api().nexus().v1().assets().get(assetId).complete();
                byte[] download = asset.download().complete();

                String filename = "%s-%s.%s".formatted(asset.maven2().artifactId(), asset.maven2().version(), asset.maven2().extension());

                ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename))
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(ContentType.APPLICATION_OCTET_STREAM)
                        .status(HttpCode.OK)
                        .result(download);
            });
        });
    }

    public String registerAsset(String assetId) {
        var hashCode = Hashing.sha512().hashString(System.nanoTime() + assetId + System.nanoTime(), StandardCharsets.UTF_8).toString();
        tokens.put(hashCode, assetId);
        return "%s/api/v1/download/proxy?token=%s".formatted(download.v1().api().configuration().config().api().url(), hashCode);
    }
}
