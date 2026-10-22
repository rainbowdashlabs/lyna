/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.update;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.google.inject.Inject;
import de.chojo.lyna.feature.butler.service.ButlerCheckRequest;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.service.UpdateChannels;
import de.chojo.lyna.feature.manifest.service.VersionManifestService;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.util.Version;
import de.chojo.lyna.web.api.v1.download.direct.Direct;
import de.chojo.lyna.web.legacy.ButlerApi;
import io.javalin.http.HttpStatus;
import org.jetbrains.annotations.Nullable;

import java.time.Instant;
import java.util.Optional;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Update {
    private final ProductLookup products;
    private final ButlerApi butler;
    private final Direct direct;
    private final UpdateTokenExchange exchange;
    private final VersionManifestService manifests;

    @Inject
    public Update(
            ProductLookup products,
            ButlerApi butler,
            Direct direct,
            UpdateTokenExchange exchange,
            VersionManifestService manifests) {
        this.manifests = manifests;
        this.products = products;
        this.butler = butler;
        this.direct = direct;
        this.exchange = exchange;
    }

    public void init() {
        path("update", () -> {
            exchange.init();
            // ?id=<>&version=<>
            get("check", ctx -> {
                Optional<ButlerCheckRequest> fromButler = ButlerCheckRequest.malformed(ctx.queryParamMap());
                if (fromButler.isPresent()) {
                    butler.answer(ctx, fromButler.get());
                    return;
                }
                int id;
                try {
                    id = Integer.parseInt(ctx.queryParam("id"));
                } catch (NumberFormatException e) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid id");
                    return;
                }

                Optional<Product> optProduct = products.byId(id);

                if (optProduct.isEmpty()) {
                    ctx.status(HttpStatus.NOT_FOUND).result("Unknown id");
                    return;
                }
                Product product = optProduct.get();
                String versionString = ctx.queryParam("version");
                if (versionString == null) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Missing version");
                    return;
                }

                @Nullable String artifact = ctx.queryParam("artifact");
                @Nullable String minecraft = blankToNull(ctx.queryParam("minecraft"));

                @Nullable String unixString = ctx.queryParam("unix");

                long unix;
                try {
                    unix = unixString == null ? 0 : Long.parseLong(unixString);
                } catch (NumberFormatException e) {
                    ctx.status(HttpStatus.BAD_REQUEST).result("Invalid unix timestamp");
                    return;
                }

                @Nullable Instant created = unix == 0 ? null : Instant.ofEpochSecond(unix);

                UpdateResponse response;
                try {
                    response = check(product, Version.parse(versionString), created, artifact, minecraft);
                } catch (RuntimeException e) {
                    response = new UpdateResponse(false, versionString, unix);
                }

                ctx.status(HttpStatus.OK).json(withDownload(product, response, artifact));
            });
        });
    }

    /**
     * Answers from the channels the installed build hears; see {@link UpdateChannels}.
     *
     * <p>Given the Minecraft version the server runs, each channel's newest build is the newest whose
     * manifest supports it; a build without a manifest makes no claim and counts as supporting it. When
     * that hides a newer build, the answer names it as {@code newest}, so a client can say an update
     * needs another Minecraft version without being offered it.
     */
    private UpdateResponse check(
            Product product,
            Version current,
            @Nullable Instant created,
            @Nullable String artifact,
            @Nullable String minecraft) {
        UpdateChannels.Decision decision =
                UpdateChannels.decide(current, created, type -> newest(product, type, artifact, minecraft));
        UpdateResponse response = answer(current, decision);
        if (minecraft == null) return response;
        UpdateChannels.Decision unfiltered =
                UpdateChannels.decide(current, created, type -> newest(product, type, artifact, null));
        return unfiltered
                .build()
                .filter(build -> unfiltered.update() && !build.version().equals(response.latest()))
                .map(build -> response.withNewest(build.version()))
                .orElse(response);
    }

    private static UpdateResponse answer(Version current, UpdateChannels.Decision decision) {
        return decision.build()
                .map(build -> new UpdateResponse(
                        decision.update(), build.version(), build.published().getEpochSecond()))
                .orElseGet(() -> new UpdateResponse(false, current.version(), 0));
    }

    /**
     * The newest build of a channel, or the newest that runs on {@code minecraft} when it is given.
     */
    private Optional<UpdateChannels.Build> newest(
            Product product, ReleaseType type, @Nullable String artifact, @Nullable String minecraft) {
        return getDownload(product, type, artifact).flatMap(download -> download.latestAssets().stream()
                .filter(asset -> minecraft == null
                        || manifests
                                .manifest(download.coordinates(), asset.maven2().version())
                                .map(manifest -> manifest.supports(minecraft))
                                .orElse(true))
                .findFirst()
                .map(asset -> new UpdateChannels.Build(
                        asset.maven2().version(), asset.lastModified().toInstant())));
    }

    private static @Nullable String blankToNull(@Nullable String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private Optional<Download> getDownload(Product product, ReleaseType type, @Nullable String artifact) {
        return product.downloads()
                .byReleaseTypeAndArtifact(type, artifact)
                .or(() -> product.downloads().byReleaseType(type).stream().findFirst());
    }

    /**
     * Adds the public address of the build an update points at, for a free product. The build is found
     * the way the check found it: by the release type its version names and the artifact asked about.
     */
    private UpdateResponse withDownload(Product product, UpdateResponse response, @Nullable String artifact) {
        if (!product.free() || !response.update()) return response;
        try {
            ReleaseType type = Version.parse(response.latest()).type();
            return getDownload(product, type, artifact)
                    .map(download -> response.withDownload(
                            direct.link(product.id(), download.type().id(), response.latest())))
                    .orElse(response);
        } catch (RuntimeException e) {
            return response;
        }
    }

    /**
     * @param download where the build can be fetched without signing in, for a free product; null otherwise
     * @param newest   the newest build of the channels the installed one hears, when it is newer than
     *                 {@code latest} but does not run on the Minecraft version asked about; null otherwise
     */
    public record UpdateResponse(
            boolean update,
            String latest,
            long published,
            @Nullable String download,

            @JsonInclude(JsonInclude.Include.NON_NULL) @Nullable
            String newest) {
        public UpdateResponse(boolean update, String latest, long published) {
            this(update, latest, published, null, null);
        }

        public UpdateResponse withDownload(String download) {
            return new UpdateResponse(update, latest, published, download, newest);
        }

        public UpdateResponse withNewest(String newest) {
            return new UpdateResponse(update, latest, published, download, newest);
        }
    }
}
