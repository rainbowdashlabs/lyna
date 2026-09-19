/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.download.direct;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRepository;
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
import io.javalin.http.UnauthorizedResponse;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

public class Direct {
    private final ProductLookup products;
    private final Auth auth;
    private final AccountRepository accounts;
    private final AccountLicenseRepository licenses;
    private final KioskProductRepository kiosk;

    @Inject
    public Direct(
            ProductLookup products,
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
     * Refuses a product this caller may not have, by the same rule the storefront shows.
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
        path("direct", () -> {
            get("{product}/{type}/{version}", ctx -> {
                int productId = Integer.parseInt(ctx.pathParam("product"));
                requireAccess(ctx, productId);
                Product product =
                        products.byId(productId).orElseThrow(() -> new NotFoundResponse("Invalid product id"));
                var downloads = product.downloads()
                        .byType(Integer.parseInt(ctx.pathParam("type")))
                        .orElseThrow(() -> new NotFoundResponse("Invalid download type"));
                AssetXO asset = downloads
                        .assetByVersion(ctx.pathParam("version"))
                        .orElseThrow(() -> new NotFoundResponse("Unknown version"));
                String filename = DownloadFilename.of(downloads, asset);

                ctx.header("Content-Disposition", "attachment; filename=\"%s\"".formatted(filename))
                        .header("X-Content-Type-Options", "nosniff")
                        .contentType(ContentType.APPLICATION_OCTET_STREAM)
                        .status(HttpStatus.OK)
                        .result(asset.downloadStream().complete());
            });
        });
    }
}
