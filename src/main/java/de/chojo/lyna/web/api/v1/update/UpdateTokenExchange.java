/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.update;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.service.DownloadFilename;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.service.UpdateTokenService;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import de.chojo.lyna.web.api.v1.download.proxy.AssetDownload;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Where an updater trades its upgrade token for a download.
 *
 * <p>{@code POST /api/v1/update/token} with {@code Authorization: Bearer <upgrade token>} and,
 * optionally, a body naming the download type and version wanted. Without one it gets the newest build
 * of the first download the account may have, stable before dev before snapshot.
 *
 * <p>The answer carries the next upgrade token - the one sent is spent - and a link that serves the
 * build for {@link Proxy#REUSABLE_LIFETIME}, as often as needed to get it whole. The build is picked
 * before the token is spent, so asking for something that does not exist costs nothing.
 */
public class UpdateTokenExchange {
    private final UpdateTokenService service;
    private final ProductLookup products;
    private final AccountRepository accounts;
    private final Proxy proxy;
    private final ObjectMapper json = new ObjectMapper();

    /**
     * @param downloadType the download type wanted, or null for the first one the account may have
     * @param version      the version wanted, or null for the newest
     */
    public record Wanted(
            @Nullable Integer downloadType, @Nullable String version) {}

    /**
     * @param upgradeToken the token to trade next time; the one sent no longer works
     * @param downloadUrl  serves the build until {@code expiresAt}, as often as asked
     */
    public record Exchanged(
            String upgradeToken,
            String downloadUrl,
            Instant expiresAt,
            int downloadType,
            String version,
            String filename,
            long sizeBytes) {}

    @Inject
    public UpdateTokenExchange(
            UpdateTokenService service, ProductLookup products, AccountRepository accounts, Proxy proxy) {
        this.service = service;
        this.products = products;
        this.accounts = accounts;
        this.proxy = proxy;
    }

    /**
     * Mounts the route under the update API.
     */
    public void init() {
        post("token", this::exchange);
    }

    private void exchange(Context ctx) {
        Outcome<UpdateToken> resolved = service.resolve(bearer(ctx));
        if (!resolved.ok()) {
            refuse(ctx, resolved.refusal());
            return;
        }
        UpdateToken entry = resolved.value();
        Wanted wanted = wanted(ctx);
        if (wanted == null) {
            ctx.status(HttpStatus.BAD_REQUEST).result("Invalid JSON body");
            return;
        }
        Optional<Product> product = products.byId(entry.productId());
        if (product.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("The product is gone");
            return;
        }
        Set<String> allowed = product.get().free()
                ? Set.of("STABLE", "DEV", "SNAPSHOT")
                : service.releaseTypes(entry.accountId(), entry.productId());
        Optional<Download> download = download(product.get(), wanted, allowed);
        if (download.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("No download of that type you may have");
            return;
        }
        Optional<AssetXO> asset = wanted.version() == null
                ? download.get().latestAssets().stream().findFirst()
                : download.get().assetByVersion(wanted.version());
        if (asset.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("Unknown version");
            return;
        }

        Outcome<UpdateToken> traded = service.trade(entry.token());
        if (!traded.ok()) {
            refuse(ctx, traded.refusal());
            return;
        }

        AssetXO build = asset.get();
        Download chosen = download.get();
        String version = build.maven2().version();
        String filename = DownloadFilename.of(chosen, build);
        Long discordId = accounts.findLinkByAccountId(entry.accountId())
                .map(AccountIdentity::externalIdAsLong)
                .orElse(null);
        String signedFor = discordId == null ? "account(%d)".formatted(entry.accountId()) : discordId.toString();
        AssetDownload assetDownload = new AssetDownload(build.id(), () -> chosen.downloaded(version), signedFor)
                .withDownloadContext(
                        entry.productId(), chosen.id(), version, "updater", entry.accountId(), discordId, null)
                .withFilename(filename);
        String url = proxy.registerReusable(assetDownload);
        ctx.status(HttpStatus.CREATED)
                .json(new Exchanged(
                        traded.value().token(),
                        url,
                        Instant.now().plus(Proxy.REUSABLE_LIFETIME),
                        chosen.type().id(),
                        version,
                        filename,
                        build.fileSize()));
    }

    private static Optional<Download> download(Product product, Wanted wanted, Set<String> allowed) {
        return product.downloads().downloads().stream()
                .filter(download ->
                        wanted.downloadType() == null || download.type().id() == wanted.downloadType())
                .filter(download ->
                        allowed.contains(download.type().releaseType().name()))
                .sorted()
                .findFirst();
    }

    private static void refuse(Context ctx, UpdateTokenService.Refusal refusal) {
        HttpStatus status =
                switch (refusal) {
                    case UNKNOWN, REUSED -> HttpStatus.UNAUTHORIZED;
                    case NO_ACCESS -> HttpStatus.FORBIDDEN;
                    case TOO_SOON -> HttpStatus.TOO_MANY_REQUESTS;
                    default -> HttpStatus.BAD_REQUEST;
                };
        ctx.status(status).result(refusal.name());
    }

    private static @Nullable String bearer(Context ctx) {
        String header = ctx.header("Authorization");
        return header != null && header.startsWith("Bearer ")
                ? header.substring("Bearer ".length()).trim()
                : null;
    }

    private @Nullable Wanted wanted(Context ctx) {
        if (ctx.body().isBlank()) return new Wanted(null, null);
        try {
            Wanted wanted = json.readValue(ctx.body(), Wanted.class);
            return wanted == null ? new Wanted(null, null) : wanted;
        } catch (Exception e) {
            return null;
        }
    }
}
