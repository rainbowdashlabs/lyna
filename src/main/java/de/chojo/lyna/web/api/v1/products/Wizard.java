/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.service.DownloadFilename;
import de.chojo.lyna.feature.download.service.ProductVersionService;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.web.api.auth.Auth;
import de.chojo.lyna.web.api.v1.download.proxy.AssetDownload;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HttpStatus;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * The four questions the download wizard asks, in the order the bot asks them: which release type,
 * which version, which download type, and then the link itself.
 *
 * <p>Each answer says how many options there were, so the page can skip a step nobody has a choice
 * about - which is the same shortcut the slash command takes.
 *
 * <p>A client that would rather not walk a wizard can call the same four in sequence; the last one
 * mints the one-time address the browser is sent to, which is the address the bot hands out too.
 */
public class Wizard {
    /** Enough history to find a known-good build, not the whole archive. */
    private static final int DEFAULT_VERSION_LIMIT = 25;

    private static final int MAX_VERSION_LIMIT = 100;

    private static final Set<String> ALL_RELEASE_TYPES =
            Set.of(ReleaseType.STABLE.name(), ReleaseType.DEV.name(), ReleaseType.SNAPSHOT.name());

    private final Proxy proxy;
    private final ProductLookup products;
    private final KioskProductRepository kiosk;
    private final Auth auth;
    private final AccountRepository accounts;
    private final AccountLicenseRepository licenses;
    private final ProductVersionService versions;

    @Inject
    public Wizard(
            Proxy proxy,
            ProductLookup products,
            KioskProductRepository kiosk,
            Auth auth,
            AccountRepository accounts,
            AccountLicenseRepository licenses,
            ProductVersionService versions) {
        this.versions = versions;
        this.proxy = proxy;
        this.products = products;
        this.kiosk = kiosk;
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
    }

    public void init() {
        path("products/{product}", () -> {
            get("release-types", this::releaseTypes);
            get("release-types/{releaseType}/versions", this::versions);
            get("versions/{version}/download-types", this::downloadTypes);
            post("versions/{version}/downloads/{downloadType}/issue", this::issue);
        });
    }

    private void releaseTypes(Context ctx) {
        int productId = productId(ctx);
        ctx.json(versions.releaseTypes(resolve(productId), downloadable(ctx, productId)));
    }

    private void versions(Context ctx) {
        ctx.json(versions.versions(resolve(productId(ctx)), releaseType(ctx), limit(ctx)));
    }

    private void downloadTypes(Context ctx) {
        int productId = productId(ctx);
        Set<String> allowed = allowed(ctx, productId);
        Product product = resolve(productId);
        String version = ctx.pathParam("version");

        List<DownloadTypeView> views = product.downloads().downloads().stream()
                .filter(download ->
                        allowed.contains(download.type().releaseType().name()))
                .filter(download -> download.assetByVersion(version).isPresent())
                .map(download -> {
                    DownloadType type = download.type();
                    return new DownloadTypeView(type.id(), type.name(), type.description());
                })
                .sorted(Comparator.comparing(DownloadTypeView::name))
                .toList();
        if (views.isEmpty()) throw new NotFoundResponse("No download carries that version");
        ctx.json(views);
    }

    private void issue(Context ctx) {
        int productId = productId(ctx);
        Set<String> allowed = allowed(ctx, productId);
        Product product = resolve(productId);
        String version = ctx.pathParam("version");
        int downloadTypeId = pathInt(ctx, "downloadType");

        Download download = product.downloads()
                .byType(downloadTypeId)
                .orElseThrow(() -> new NotFoundResponse("Unknown download type"));
        if (!allowed.contains(download.type().releaseType().name())) {
            throw new ForbiddenResponse("You may not download that release type");
        }
        AssetXO asset = download.assetByVersion(version).orElseThrow(() -> new NotFoundResponse("Unknown version"));

        var session = auth.currentSession(ctx);
        Integer accountId = session.map(verified -> verified.accountId()).orElse(null);
        Long discordId = session.flatMap(verified -> accounts.findLinkByAccountId(verified.accountId()))
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
        String actor = discordId == null ? "anonymous(%s)".formatted(ctx.ip()) : Long.toString(discordId);

        AssetDownload assetDownload = new AssetDownload(
                        asset.id(), () -> download.downloaded(asset.maven2().version()), actor)
                .withDownloadContext(
                        product.id(),
                        download.id(),
                        asset.maven2().version(),
                        product.free() ? "free" : "license",
                        accountId,
                        discordId,
                        null);
        String filename = DownloadFilename.of(download, asset);
        String url = proxy.registerAsset(assetDownload.withFilename(filename));
        ctx.status(HttpStatus.CREATED)
                .json(new IssuedDownload(
                        url, filename, (long) asset.fileSize(), Instant.now().plusSeconds(1800)));
    }

    /**
     * The release types the caller may download, for saying so next to a list anybody may read. Unlike
     * {@link #allowed}, having none is an answer rather than a refusal.
     */
    private Set<String> downloadable(Context ctx, int productId) {
        if (kiosk.isFree(productId)) return ALL_RELEASE_TYPES;
        return auth.currentSession(ctx)
                .map(session -> licenses.releaseTypes(session.accountId(), productId))
                .orElse(Set.of());
    }

    /**
     * The release types the caller may have of this product.
     *
     * <p>Free means all of them, for anybody. Otherwise it is what the caller's licenses carry, and
     * an empty answer is a refusal - told apart from "not signed in" so the storefront can offer a
     * sign-in to one and a purchase to the other.
     */
    private Set<String> allowed(Context ctx, int productId) {
        if (kiosk.isFree(productId)) return ALL_RELEASE_TYPES;
        var session = auth.currentSession(ctx);
        if (session.isEmpty()) throw new UnauthorizedResponse("Sign in to download this product");
        Set<String> types = licenses.releaseTypes(session.get().accountId(), productId);
        if (types.isEmpty()) throw new ForbiddenResponse("You do not hold a license for this product");
        return types;
    }

    private Product resolve(int productId) {
        return products.byId(productId).orElseThrow(() -> new NotFoundResponse("Invalid product id"));
    }

    private static int productId(Context ctx) {
        return pathInt(ctx, "product");
    }

    private static int pathInt(Context ctx, String name) {
        try {
            return Integer.parseInt(ctx.pathParam(name));
        } catch (NumberFormatException e) {
            throw new NotFoundResponse("Invalid " + name);
        }
    }

    private static ReleaseType releaseType(Context ctx) {
        return Optional.ofNullable(ctx.pathParam("releaseType"))
                .map(raw -> {
                    try {
                        return ReleaseType.valueOf(raw.toUpperCase(java.util.Locale.ROOT));
                    } catch (IllegalArgumentException e) {
                        throw new NotFoundResponse("Unknown release type");
                    }
                })
                .orElseThrow(() -> new NotFoundResponse("Unknown release type"));
    }

    private static int limit(Context ctx) {
        String raw = ctx.queryParam("limit");
        if (raw == null) return DEFAULT_VERSION_LIMIT;
        try {
            return Math.min(Math.max(Integer.parseInt(raw), 1), MAX_VERSION_LIMIT);
        } catch (NumberFormatException e) {
            return DEFAULT_VERSION_LIMIT;
        }
    }

    public record DownloadTypeView(int id, String name, String description) {}

    /**
     * @param url       the one-time address the browser is sent to
     * @param expiresAt when the address stops working if nobody uses it
     */
    public record IssuedDownload(String url, String filename, Long sizeBytes, Instant expiresAt) {}
}
