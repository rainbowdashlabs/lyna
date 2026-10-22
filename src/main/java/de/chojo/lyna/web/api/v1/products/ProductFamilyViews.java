/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.download.service.DownloadAccessService;
import de.chojo.lyna.feature.icon.repository.ProductIconRepository;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.service.ProductFamilyService;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Add-ons and parents as the account pages name them, for an account's own lists and licences.
 */
@Singleton
public class ProductFamilyViews {
    private final KioskProductRepository kiosk;
    private final ProductIconRepository icons;
    private final DownloadAccessService access;

    @Inject
    public ProductFamilyViews(KioskProductRepository kiosk, ProductIconRepository icons, DownloadAccessService access) {
        this.kiosk = kiosk;
        this.icons = icons;
        this.access = access;
    }

    /**
     * @return every product, by id, to find a parent's name in
     */
    public Map<Integer, KioskProduct> catalogue() {
        return kiosk.all().stream().collect(Collectors.toMap(KioskProduct::id, Function.identity()));
    }

    /**
     * @return the add-ons of a product, each saying whether the account may download it already
     */
    public List<RelatedProduct> addons(int productId, int accountId) {
        List<KioskProduct> catalogue = kiosk.all();
        return catalogue.stream()
                .filter(product -> product.id() == productId)
                .findFirst()
                .map(product -> RelatedProduct.of(
                        ProductFamilyService.family(product, catalogue).addons(),
                        icons.withIcon(),
                        access.entitledProductIds(accountId)))
                .orElse(List.of());
    }
}
