/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.product.entity.ProductLinks;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The addresses a product's purchase mail fills in.
 */
@Singleton
public class ProductLinksRepository {
    /**
     * @return the product's links, or {@link ProductLinks#NONE} for a product that does not exist
     */
    public ProductLinks get(int productId) {
        return query("SELECT wiki_url, review_url FROM product WHERE id = ?")
                .single(call().bind(productId))
                .map(row -> new ProductLinks(row.getString("wiki_url"), row.getString("review_url")))
                .first()
                .orElse(ProductLinks.NONE);
    }

    public void set(int productId, ProductLinks links) {
        query("UPDATE product SET wiki_url = ?, review_url = ? WHERE id = ?")
                .single(call().bind(links.wikiUrl()).bind(links.reviewUrl()).bind(productId))
                .update();
    }
}
