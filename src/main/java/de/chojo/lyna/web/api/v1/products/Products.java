/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
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
    private final Auth auth;
    private final AccountRepository accounts;
    private final AccountLicenseRepository licenses;

    @Inject
    public Products(
            KioskProductRepository kiosk, Auth auth, AccountRepository accounts, AccountLicenseRepository licenses) {
        this.kiosk = kiosk;
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
    }

    public void init() {
        path("products", () -> {
            get(this::list);
            path("{productId}", () -> get(this::detail));
        });
    }

    private void list(Context ctx) {
        Set<Integer> entitled = entitlements(ctx);
        List<KioskEntry> entries = kiosk.all().stream()
                .map(product -> KioskEntry.of(product, entitled.contains(product.id())))
                .toList();
        ctx.json(entries);
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
                        product -> ctx.json(
                                KioskDetail.of(product, entitlements(ctx).contains(product.id()))),
                        () -> ctx.status(HttpStatus.NOT_FOUND));
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
        static KioskEntry of(KioskProduct product, boolean entitled) {
            return new KioskEntry(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    product.iconUrl(),
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
        static KioskDetail of(KioskProduct product, boolean entitled) {
            return new KioskDetail(
                    product.id(),
                    Long.toString(product.guildId()),
                    product.name(),
                    product.url(),
                    product.iconUrl(),
                    product.free(),
                    product.purchaseUrl(),
                    entitled,
                    product.description());
        }
    }
}
