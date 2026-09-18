/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.releases;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.DownloadType;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.web.api.auth.Auth;
import de.chojo.nexus.entities.AssetXO;
import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.NotFoundResponse;
import io.javalin.http.UnauthorizedResponse;

import java.time.Instant;
import java.util.List;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Releases {
    private final de.chojo.lyna.data.access.Products products;
    private final Auth auth;
    private final AccountRepository accounts;
    private final AccountLicenseRepository licenses;
    private final KioskProductRepository kiosk;

    @Inject
    public Releases(
            de.chojo.lyna.data.access.Products products,
            Auth auth,
            AccountRepository accounts,
            AccountLicenseRepository licenses,
            KioskProductRepository kiosk) {
        this.products = products;
        this.auth = auth;
        this.accounts = accounts;
        this.licenses = licenses;
        this.kiosk = kiosk;
    }

    /**
     * Refuses a product this caller may not have.
     *
     * <p>A free product is everybody's. A premium one belongs to whoever holds a license covering
     * it, which is the same rule the bot applies; the two answers are told apart so the storefront
     * can offer a sign-in to one and a purchase to the other.
     */
    private void requireAccess(Context ctx, int productId) {
        if (kiosk.isFree(productId)) return;
        var session = auth.currentSession(ctx);
        if (session.isEmpty()) throw new UnauthorizedResponse("Sign in to download this product");
        boolean entitled =
                licenses.entitledProductIds(session.get().accountId()).contains(productId);
        if (!entitled) throw new ForbiddenResponse("You do not hold a license for this product");
    }

    public void init() {
        path("releases", () -> {
            get("{product}", ctx -> {
                int productId = Integer.parseInt(ctx.pathParam("product"));
                requireAccess(ctx, productId);
                Product product =
                        products.byId(productId).orElseThrow(() -> new NotFoundResponse("Invalid product id"));

                List<SimpleType> downloads = product.downloads().downloads().stream()
                        .sorted()
                        .map(SimpleType::create)
                        .toList();
                ctx.json(downloads);
            });

            get("{product}/{type}", ctx -> {
                int productId = Integer.parseInt(ctx.pathParam("product"));
                requireAccess(ctx, productId);
                Product product =
                        products.byId(productId).orElseThrow(() -> new NotFoundResponse("Invalid product id"));

                Download download = product.downloads()
                        .byType(Integer.parseInt(ctx.pathParam("type")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid type id"));
                var assets = download.latestAssets().stream()
                        .map(e -> SimpleAsset.create(download.type().id(), e))
                        .toList();
                ctx.json(assets);
            });
        });
    }

    private record SimpleType(int id, String name, String description) {
        public static SimpleType create(Download download) {
            DownloadType type = download.type();
            return new SimpleType(type.id(), type.name(), type.description());
        }
    }

    private record SimpleAsset(String version, int type, Instant published) {
        public static SimpleAsset create(int type, AssetXO asset) {
            return new SimpleAsset(
                    asset.maven2().version(), type, asset.lastModified().toInstant());
        }
    }
}
