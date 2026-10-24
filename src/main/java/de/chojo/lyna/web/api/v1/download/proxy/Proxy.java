/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.download.proxy;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import de.chojo.jdautil.util.SnowflakeCreator;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.configuration.elements.Downloads;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.download.repository.DownloadLogRepository;
import de.chojo.lyna.util.JarUtil;
import de.chojo.nexus.NexusRest;
import io.javalin.http.ContentType;
import io.javalin.http.HttpStatus;
import org.intellij.lang.annotations.Language;
import org.slf4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.time.Duration;
import java.util.HashMap;
import java.util.HexFormat;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.TimeUnit;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * Serves a build from Nexus to whoever holds a token for it, signed with who it was issued to.
 *
 * <p>A token from {@link #registerAsset} is spent by its first use. One from {@link #registerReusable}
 * works until it expires, for an updater that has to be able to retry a download that broke off.
 */
public class Proxy {
    /** How long a reusable link lasts. */
    public static final Duration REUSABLE_LIFETIME = Duration.ofMinutes(5);

    /** Where a plugin's build data asks for the key of the license it was downloaded under. */
    public static final String LYNA_LICENSE = "%%__LYNA_LICENSE__%%";

    private static final Logger log = getLogger(Proxy.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private final NexusRest nexus;
    private final Api apiSettings;
    private final Cache<String, AssetDownload> tokens =
            CacheBuilder.newBuilder().expireAfterWrite(30, TimeUnit.MINUTES).build();
    private final Cache<String, AssetDownload> reusable =
            CacheBuilder.newBuilder().expireAfterWrite(REUSABLE_LIFETIME).build();

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
    private final DownloadLogRepository downloadLog;
    private final Downloads downloads;
    private final AccountLicenseRepository licenses;

    @Inject
    public Proxy(
            NexusRest nexus,
            Api apiSettings,
            DownloadLogRepository downloadLog,
            Downloads downloads,
            AccountLicenseRepository licenses) {
        this.licenses = licenses;
        this.nexus = nexus;
        this.apiSettings = apiSettings;
        this.downloadLog = downloadLog;
        this.downloads = downloads;
    }

    /**
     * The key of the license the download was made under, which the jar carries in place of
     * {@link #LYNA_LICENSE}. Role access, trials and free products have none, and their jars keep the
     * placeholder.
     */
    private Optional<String> licenseKey(AssetDownload download) {
        if (download.accountId() == null || download.productId() == null) return Optional.empty();
        return licenses.keyFor(download.accountId(), download.productId());
    }

    public void init() {
        path(
                "proxy",
                () -> get(ctx -> {
                    String token = ctx.queryParam("token");
                    if (token == null) {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        ctx.result("Missing token");
                        return;
                    }
                    AssetDownload download = tokens.getIfPresent(token);
                    if (download != null) tokens.invalidate(token);
                    else download = reusable.getIfPresent(token);
                    if (download == null) {
                        ctx.status(HttpStatus.BAD_REQUEST);
                        ctx.result("Invalid token.");
                        return;
                    }

                    String agent = ctx.header("User-Agent");
                    if (agent != null && agent.toLowerCase(Locale.ROOT).contains("discordbot")) {
                        ctx.status(HttpStatus.OK)
                                .contentType(ContentType.TEXT_HTML)
                                .result(shareHtml);
                        return;
                    }

                    var asset = nexus.v1().assets().get(download.assetId()).complete();
                    String filename = download.filename() != null
                            ? download.filename()
                            : "%s-%s.%s"
                                    .formatted(
                                            asset.maven2().artifactId(),
                                            asset.maven2().version(),
                                            asset.maven2().extension());

                    download.postDownload().run();

                    if (download.productId() != null
                            && download.downloadId() != null
                            && download.version() != null
                            && download.source() != null) {
                        try {
                            downloadLog.record(
                                    download.accountId(),
                                    download.discordId(),
                                    download.licenseId(),
                                    download.productId(),
                                    download.downloadId(),
                                    download.version(),
                                    download.source(),
                                    ctx.header("User-Agent"),
                                    ipHash(ctx.ip()));
                        } catch (Exception e) {
                            log.warn(LogNotify.NOTIFY_ADMIN, "Failed to record download log entry", e);
                        }
                    }

                    var complete = asset.downloadStream().complete();
                    ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename))
                            .header("X-Content-Type-Options", "nosniff")
                            .contentType(ContentType.APPLICATION_OCTET_STREAM)
                            .status(HttpStatus.OK);

                    if (downloads.skipJarSigning()) {
                        ctx.result(complete);
                        return;
                    }

                    Map<String, String> replacements = new HashMap<>(Map.of(
                            "%%__USER__%%", download.userId(),
                            "%%__RESOURCE__%%", download.assetId(),
                            "%%__NONCE__%%", snowflakeCreator.nextString()));
                    licenseKey(download).ifPresent(key -> replacements.put(LYNA_LICENSE, key));

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
                }));
    }

    public String registerAsset(AssetDownload assetDownload) {
        return register(tokens, assetDownload);
    }

    /**
     * @return a link that serves the build as often as asked for {@link #REUSABLE_LIFETIME}
     */
    public String registerReusable(AssetDownload assetDownload) {
        return register(reusable, assetDownload);
    }

    private String register(Cache<String, AssetDownload> cache, AssetDownload assetDownload) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        String token = HexFormat.of().formatHex(bytes);
        cache.put(token, assetDownload);
        return "%s/api/v1/download/proxy?token=%s".formatted(apiSettings.url(), token);
    }

    private static String ipHash(String ip) {
        if (ip == null || ip.isBlank()) return null;
        return Hashing.sha256().hashString(ip, StandardCharsets.UTF_8).toString();
    }
}
