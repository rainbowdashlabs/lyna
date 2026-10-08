/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.google.inject.Inject;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.download.service.ProductVersionService;
import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import de.chojo.lyna.feature.icon.service.ProductIconService;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.kiosk.service.ProductPageService;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

/**
 * The storefront catalogue.
 *
 * <p>Every product, not only the free ones: a premium product a visitor cannot download is still
 * something they may want to buy, and a tile that is missing tells them nothing. What each visitor
 * may do with a product is answered per request from the licenses their Discord id holds.
 */
public class Products {
    private final KioskProductRepository kiosk;
    private final ProductIconService productIcons;
    private final ProductIconRepository icons;
    private final Auth auth;
    private final AccountRepository accounts;
    private final AccountLicenseRepository licenses;

    private final ProductPageService pages;
    private final ProductLookup productLookup;
    private final ProductVersionService versions;

    @Inject
    public Products(
            KioskProductRepository kiosk,
            Auth auth,
            AccountRepository accounts,
            AccountLicenseRepository licenses,
            ProductIconRepository icons,
            ProductIconService productIcons,
            ProductPageService pages,
            ProductLookup productLookup,
            ProductVersionService versions) {
        this.pages = pages;
        this.productLookup = productLookup;
        this.versions = versions;
        this.productIcons = productIcons;
        this.icons = icons;
        this.kiosk = kiosk;
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
    }

    public void init() {
        path("products", () -> {
            get(this::list);
            path("{productId}", () -> {
                get(this::detail);
                get("icon", this::icon);
            });
        });
    }

    private void list(Context ctx) {
        Set<Integer> entitled = entitlements(ctx);
        Set<Integer> uploaded = icons.withIcon();
        List<KioskEntry> entries = kiosk.all().stream()
                .map(product -> KioskEntry.of(
                        product,
                        entitled.contains(product.id()),
                        uploaded.contains(product.id()),
                        latestStable(product.id())))
                .toList();
        ctx.json(entries);
    }

    /**
     * Where a tile fetches the icon from.
     *
     * <p>An uploaded icon is served by this instance; otherwise whatever address an operator
     * configured, which may be nothing at all.
     */
    private static String iconAddress(KioskProduct product, boolean uploaded) {
        return uploaded ? "/api/v1/products/%d/icon".formatted(product.id()) : product.iconUrl();
    }

    /**
     * One product, with what it says about itself.
     *
     * <p>The description is here rather than in the catalogue because it is markdown and a grid of
     * tiles has no use for it - a list of forty products would carry forty descriptions nobody reads.
     */
    private void detail(Context ctx) {
        int productId;
        try {
            productId = Integer.parseInt(ctx.pathParam("productId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        kiosk.byId(productId)
                .ifPresentOrElse(
                        product -> ctx.json(KioskDetail.of(
                                product,
                                entitlements(ctx).contains(product.id()),
                                icons.of(product.id()).isPresent(),
                                pages.page(product))),
                        () -> ctx.status(HttpStatus.NOT_FOUND));
    }

    /**
     * Serves a product's icon at the size asked for.
     *
     * <p>Answered with the moment it last changed, so a browser that has it already is told to keep
     * it. Icons change rarely and are drawn on every tile of the storefront.
     */
    private void icon(Context ctx) {
        int productId;
        try {
            productId = Integer.parseInt(ctx.pathParam("productId"));
        } catch (NumberFormatException e) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        var stored = icons.of(productId);
        if (stored.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        int requested;
        try {
            requested =
                    Integer.parseInt(ctx.queryParamAsClass("size", String.class).getOrDefault("128"));
        } catch (NumberFormatException e) {
            requested = 128;
        }
        var bytes = productIcons.read(productId, requested);
        if (bytes.isEmpty()) {
            ctx.status(HttpStatus.NOT_FOUND);
            return;
        }
        String tag = "\"%d-%d-%d\""
                .formatted(productId, requested, stored.get().updatedAt().toEpochMilli());
        if (tag.equals(ctx.header("If-None-Match"))) {
            ctx.status(HttpStatus.NOT_MODIFIED);
            return;
        }
        ctx.header("ETag", tag);
        ctx.header("Cache-Control", "public, max-age=86400");
        ctx.contentType(stored.get().mime()).result(bytes.get());
    }

    /**
     * What the caller may already download.
     *
     * <p>Anonymous, unlinked and unknown all mean the same thing here - nothing entitled - so none
     * of them is an error. A free product is downloadable regardless and is not listed.
     */
    private Set<Integer> entitlements(Context ctx) {
        return auth.currentSession(ctx)
                .map(session -> licenses.entitledProductIds(session.accountId()))
                .orElse(Set.of());
    }

    /**
     * @param entitled whether this visitor holds a license covering the product
     */
    /**
     * The newest stable build of a product, for its tile. Nothing when it has none, or when Nexus
     * cannot be asked - a tile without a version is better than a storefront that does not load.
     */
    private Optional<ProductVersionService.VersionView> latestStable(int productId) {
        try {
            return productLookup
                    .byId(productId)
                    .flatMap(product -> versions.versions(product, ReleaseType.STABLE, 1).stream()
                            .findFirst());
        } catch (RuntimeException e) {
            return Optional.empty();
        }
    }

    /**
     * @param latestVersion the newest stable version, or null when there is none
     * @param updatedAt     when it was published, or null when there is none
     */
    private record KioskEntry(
            int id,
            String guildId,
            String name,
            String url,
            String iconUrl,
            boolean free,
            String purchaseUrl,
            boolean entitled,
            String latestVersion,
            @JsonFormat(shape = JsonFormat.Shape.STRING) Instant updatedAt) {
        static KioskEntry of(
                KioskProduct product,
                boolean entitled,
                boolean uploaded,
                Optional<ProductVersionService.VersionView> latest) {
            return new KioskEntry(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    iconAddress(product, uploaded),
                    product.free(),
                    product.purchaseUrl(),
                    entitled,
                    latest.map(ProductVersionService.VersionView::version).orElse(null),
                    latest.map(ProductVersionService.VersionView::publishedAt).orElse(null));
        }
    }

    /**
     * A product on its own page: everything a tile shows, and the prose a tile has no room for.
     *
     * @param description markdown, rendered where it is shown; null when nobody has written any
     */
    /**
     * @param description what the page shows: the product's own description or its README
     * @param pageSource  which of the two it is
     * @param readmeUrl   the README's page on GitHub, when the page shows it
     * @param trial       whether somebody without a license may download it once to try it
     */
    private record KioskDetail(
            int id,
            String guildId,
            String name,
            String url,
            String iconUrl,
            boolean free,
            String purchaseUrl,
            boolean entitled,
            String description,
            ProductPageService.Source pageSource,
            String readmeUrl,
            boolean trial) {
        static KioskDetail of(KioskProduct product, boolean entitled, boolean uploaded, ProductPageService.Page page) {
            return new KioskDetail(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    iconAddress(product, uploaded),
                    product.free(),
                    product.purchaseUrl(),
                    entitled,
                    page.markdown(),
                    page.source(),
                    page.readmeUrl(),
                    product.trial());
        }
    }
}
