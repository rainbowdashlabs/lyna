/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import de.chojo.lyna.feature.icon.service.ProductIconService;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.web.api.auth.Auth;
import io.javalin.http.Context;
import io.javalin.http.HttpStatus;

import java.util.List;
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

    @Inject
    public Products(
            KioskProductRepository kiosk,
            Auth auth,
            AccountRepository accounts,
            AccountLicenseRepository licenses,
            ProductIconRepository icons,
            ProductIconService productIcons) {
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
                .map(product ->
                        KioskEntry.of(product, entitled.contains(product.id()), uploaded.contains(product.id())))
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
                                icons.of(product.id()).isPresent())),
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
    private record KioskEntry(
            int id,
            String guildId,
            String name,
            String url,
            String iconUrl,
            boolean free,
            String purchaseUrl,
            boolean entitled) {
        static KioskEntry of(KioskProduct product, boolean entitled, boolean uploaded) {
            return new KioskEntry(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    iconAddress(product, uploaded),
                    product.free(),
                    product.purchaseUrl(),
                    entitled);
        }
    }

    /**
     * A product on its own page: everything a tile shows, and the prose a tile has no room for.
     *
     * @param description markdown, rendered where it is shown; null when nobody has written any
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
            String description) {
        static KioskDetail of(KioskProduct product, boolean entitled, boolean uploaded) {
            return new KioskDetail(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    iconAddress(product, uploaded),
                    product.free(),
                    product.purchaseUrl(),
                    entitled,
                    product.description());
        }
    }
}
