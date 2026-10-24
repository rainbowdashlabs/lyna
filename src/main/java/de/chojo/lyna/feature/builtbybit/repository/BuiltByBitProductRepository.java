/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Which product a BuiltByBit resource or add-on is sold as.
 *
 * <p>A listing names at most one product and a product is sold as at most one listing, which is what
 * lets a purchase of the listing issue a licence for exactly one product.
 */
@Singleton
public class BuiltByBitProductRepository {

    /**
     * @return the product sold as this listing
     */
    public Optional<Integer> productFor(BuiltByBitListing listing) {
        return query("SELECT product_id FROM builtbybit_product WHERE resource_id = ? AND addon_id = ?")
                .single(call().bind(listing.resourceId()).bind(listing.addonId()))
                .map(row -> row.getInt("product_id"))
                .first();
    }

    /**
     * @return the listing the product is sold as
     */
    public Optional<BuiltByBitListing> listingOf(int productId) {
        return query("SELECT resource_id, addon_id FROM builtbybit_product WHERE product_id = ?")
                .single(call().bind(productId))
                .map(this::listing)
                .first();
    }

    /**
     * @return the listing each product is sold as, by product id, for the admin list to show
     */
    public Map<Integer, BuiltByBitListing> listingsByProduct() {
        return query("SELECT product_id, resource_id, addon_id FROM builtbybit_product")
                .single()
                .map(row -> Map.entry(row.getInt("product_id"), listing(row)))
                .all()
                .stream()
                .collect(Collectors.toMap(Map.Entry::getKey, Map.Entry::getValue));
    }

    /**
     * @return whether the listing is already sold as a product other than this one
     */
    public boolean takenByAnother(BuiltByBitListing listing, int productId) {
        return productFor(listing).filter(owner -> owner != productId).isPresent();
    }

    /**
     * Sells a product as the listing, replacing any it was sold as. Fails on a listing another product
     * is sold as; ask {@link #takenByAnother(BuiltByBitListing, int)} first.
     */
    public void assign(int productId, BuiltByBitListing listing) {
        query("""
                INSERT INTO builtbybit_product (product_id, resource_id, addon_id) VALUES (?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE
                    SET resource_id = excluded.resource_id, addon_id = excluded.addon_id
                """)
                .single(call().bind(productId).bind(listing.resourceId()).bind(listing.addonId()))
                .insert();
    }

    public void clear(int productId) {
        query("DELETE FROM builtbybit_product WHERE product_id = ?")
                .single(call().bind(productId))
                .delete();
    }

    private BuiltByBitListing listing(Row row) throws SQLException {
        return new BuiltByBitListing(row.getLong("resource_id"), row.getLong("addon_id"));
    }
}
