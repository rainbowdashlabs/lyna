/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.v1.products;

import de.chojo.lyna.feature.kiosk.entity.KioskProduct;

import java.util.List;
import java.util.Set;

/**
 * A product named on another one's page: the one it is an add-on of, its add-ons, or the add-ons it
 * sits beside. Enough to link to it and say whether this visitor has it already.
 *
 * @param entitled whether this visitor holds a license covering it
 */
public record RelatedProduct(int id, String name, String iconUrl, boolean free, boolean entitled) {
    /**
     * @param uploaded the products whose icon this instance serves itself
     * @param entitled the products this visitor holds a license for
     */
    public static RelatedProduct of(KioskProduct product, Set<Integer> uploaded, Set<Integer> entitled) {
        return new RelatedProduct(
                product.id(),
                product.name(),
                iconAddress(product, uploaded.contains(product.id())),
                product.free(),
                entitled.contains(product.id()));
    }

    /**
     * @see #of(KioskProduct, Set, Set)
     */
    public static List<RelatedProduct> of(List<KioskProduct> products, Set<Integer> uploaded, Set<Integer> entitled) {
        return products.stream().map(product -> of(product, uploaded, entitled)).toList();
    }

    /**
     * Where a tile fetches the icon from.
     *
     * <p>An uploaded icon is served by this instance; otherwise whatever address an operator
     * configured, which may be nothing at all.
     */
    public static String iconAddress(KioskProduct product, boolean uploaded) {
        return uploaded ? "/api/v1/products/%d/icon".formatted(product.id()) : product.iconUrl();
    }
}
