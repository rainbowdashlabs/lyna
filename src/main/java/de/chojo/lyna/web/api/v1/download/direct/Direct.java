/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.download.direct;

import com.google.common.hash.Hashing;
import com.google.inject.Inject;
import de.chojo.logutil.marker.LogNotify;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.repository.DownloadLogRepository;
import de.chojo.lyna.feature.download.service.DownloadFilename;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.web.api.auth.Auth;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.ContentType;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import org.slf4j.Logger;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static org.slf4j.LoggerFactory.getLogger;

/**
 * A lasting address for one build of a free product: the public link the wizard and the update check
 * hand out for one.
 *
 * <p>A paid product is refused here, licence or not. Its builds go through a one-time token from the
 * wizard or the updater, which is where a jar is stamped with who it was issued to and the key of their
 * licence; a lasting address would hand the same jar out unstamped. {@value #LATEST} in place of a
 * version names the newest build of the download, so an updater can fetch the current one without
 * asking which it is.
 */
public class Direct {
    private static final Logger log = getLogger(Direct.class);
    public static final String LATEST = "latest";
    private final ProductLookup products;
    private final Auth auth;
    private final AccountRepository accounts;
    private final KioskProductRepository kiosk;
    private final DownloadLogRepository downloadLog;
    private final Api api;

    @Inject
    public Direct(
            ProductLookup products,
            Auth auth,
            AccountRepository accounts,
            KioskProductRepository kiosk,
            DownloadLogRepository downloadLog,
            Api api) {
        this.products = products;
        this.auth = auth;
        this.accounts = accounts;
        this.kiosk = kiosk;
        this.downloadLog = downloadLog;
        this.api = api;
    }

    /**
     * The address of one build of a download.
     */
    public String link(int productId, int downloadTypeId, String version) {
        return "%s/api/v1/download/direct/%d/%d/%s"
                .formatted(
                        api.url(),
                        productId,
                        downloadTypeId,
                        URLEncoder.encode(version, StandardCharsets.UTF_8).replace("+", "%20"));
    }

    /**
     * Refuses a build of a paid product: those are handed out through a one-time token only.
     */
    private void requireFree(int productId) {
        if (!kiosk.isFree(productId)) {
            throw new ForbiddenResponse("Paid builds are downloaded through the download wizard");
        }
    }

    public void init() {
        path(
                "direct",
                () -> get("{product}/{type}/{version}", ctx -> {
                    int productId = Integer.parseInt(ctx.pathParam("product"));
                    Product product =
                            products.byId(productId).orElseThrow(() -> new NotFoundResponse("Invalid product id"));
                    var downloads = product.downloads()
                            .byType(Integer.parseInt(ctx.pathParam("type")))
                            .orElseThrow(() -> new NotFoundResponse("Invalid download type"));
                    requireFree(productId);
                    String version = ctx.pathParam("version");
                    AssetXO asset = (LATEST.equals(version)
                                    ? downloads.latestAssets().stream().findFirst()
                                    : downloads.assetByVersion(version))
                            .orElseThrow(() -> new NotFoundResponse("Unknown version"));
                    String filename = DownloadFilename.of(downloads, asset);
                    record(ctx, product, downloads, asset.maven2().version());

                    ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename))
                            .header("X-Content-Type-Options", "nosniff")
                            .contentType(ContentType.APPLICATION_OCTET_STREAM)
                            .status(HttpStatus.OK)
                            .result(asset.downloadStream().complete());
                }));
    }

    /**
     * Counts the download and adds it to the log, under the caller's account when they are signed in.
     */
    private void record(Context ctx, Product product, Download download, String version) {
        download.downloaded(version);
        var session = auth.currentSession(ctx);
        Integer accountId = session.map(JwtService.Verified::accountId).orElse(null);
        Long discordId = session.flatMap(verified -> accounts.findLinkByAccountId(verified.accountId()))
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
        try {
            downloadLog.record(
                    accountId,
                    discordId,
                    null,
                    product.id(),
                    download.id(),
                    version,
                    product.free() ? "free" : "license",
                    ctx.header("User-Agent"),
                    ipHash(ctx.ip()));
        } catch (Exception e) {
            log.warn(LogNotify.NOTIFY_ADMIN, "Failed to record download log entry", e);
        }
    }

    private static String ipHash(String ip) {
        if (ip == null || ip.isBlank()) return null;
        return Hashing.sha256().hashString(ip, StandardCharsets.UTF_8).toString();
    }
}
