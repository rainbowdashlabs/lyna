/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import com.google.inject.Inject;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.service.DownloadFilename;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.trial.service.WebTrialService;
import de.chojo.lyna.web.api.auth.Auth;
import de.chojo.lyna.web.api.v1.download.proxy.AssetDownload;
import de.chojo.lyna.web.api.v1.download.proxy.Proxy;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;
import static io.javalin.apibuilder.ApiBuilder.post;

/**
 * Taking a product's trial from its page: one download of the newest stable build, as {@code /trial}
 * gives on Discord. Who may take one is {@link WebTrialService}'s to say.
 */
public class TrialApi {
    private final Auth auth;
    private final ProductLookup products;
    private final WebTrialService trials;
    private final Proxy proxy;

    /**
     * @param waitSeconds how long until a refusal for age passes, or zero
     * @param downloads   the stable downloads a trial may take, when it may be taken
     */
    public record TrialStatus(WebTrialService.Reason reason, long waitSeconds, List<TrialDownload> downloads) {}

    public record TrialDownload(int typeId, String name, String description, String version) {}

    @Inject
    public TrialApi(Auth auth, ProductLookup products, WebTrialService trials, Proxy proxy) {
        this.auth = auth;
        this.products = products;
        this.trials = trials;
        this.proxy = proxy;
    }

    public void init() {
        path("products/{product}/trial", () -> {
            get(this::status);
            post("{downloadType}/issue", this::issue);
        });
    }

    private void status(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Optional<Product> product = product(ctx);
        if (product.isEmpty()) return;
        WebTrialService.Decision decision =
                trials.decide(product.get(), session.get().accountId());
        ctx.json(new TrialStatus(
                decision.reason(),
                decision.remaining().toSeconds(),
                decision.eligible() ? downloads(product.get()) : List.of()));
    }

    /**
     * Issues the one-time link for the newest stable build of a download type and spends the trial.
     */
    private void issue(Context ctx) {
        Optional<JwtService.Verified> session = session(ctx);
        if (session.isEmpty()) return;
        Optional<Product> product = product(ctx);
        if (product.isEmpty()) return;
        int accountId = session.get().accountId();
        WebTrialService.Decision decision = trials.decide(product.get(), accountId);
        if (!decision.eligible()) {
            ctx.status(HttpStatus.FORBIDDEN).result(decision.reason().name());
            return;
        }
        Optional<Download> download;
        try {
            download = product.get()
                    .downloads()
                    .byType(Integer.parseInt(ctx.pathParam("downloadType")))
                    .filter(found -> found.type().releaseType() == ReleaseType.STABLE);
        } catch (NumberFormatException e) {
            download = Optional.empty();
        }
        Optional<AssetXO> asset =
                download.flatMap(found -> found.latestAssets().stream().findFirst());
        if (asset.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND).result("No stable build of that type");
            return;
        }
        trials.spend(product.get(), accountId);
        Download chosen = download.get();
        AssetXO build = asset.get();
        String filename = DownloadFilename.of(chosen, build);
        AssetDownload assetDownload = new AssetDownload(
                        build.id(),
                        () -> chosen.downloaded(build.maven2().version()),
                        "trial(account %d)".formatted(accountId))
                .withDownloadContext(
                        product.get().id(), chosen.id(), build.maven2().version(), "trial", accountId, null, null);
        String url = proxy.registerAsset(assetDownload.withFilename(filename));
        ctx.status(HttpStatus.CREATED)
                .json(new Wizard.IssuedDownload(
                        url, filename, (long) build.fileSize(), Instant.now().plusSeconds(1800)));
    }

    private List<TrialDownload> downloads(Product product) {
        return product.downloads().byReleaseType(ReleaseType.STABLE).stream()
                .flatMap(download -> download.latestAssets().stream()
                        .findFirst()
                        .map(asset -> new TrialDownload(
                                download.type().id(),
                                download.type().name(),
                                download.type().description(),
                                asset.maven2().version()))
                        .stream())
                .toList();
    }

    private Optional<JwtService.Verified> session(Context ctx) {
        Optional<JwtService.Verified> session = auth.currentSession(ctx);
        if (session.isEmpty()) ctx.status(HttpStatus.UNAUTHORIZED).result("Sign in to take a trial");
        return session;
    }

    private Optional<Product> product(Context ctx) {
        Optional<Product> product;
        try {
            product = products.byId(Integer.parseInt(ctx.pathParam("product")));
        } catch (NumberFormatException e) {
            product = Optional.empty();
        }
        if (product.isEmpty()) ctx.status(HttpStatus.NOT_FOUND);
        return product;
    }
}
