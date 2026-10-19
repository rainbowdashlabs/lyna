/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.entity;

/**
 * Where a product's purchase mail points a buyer, beyond the product's own address.
 *
 * @param wikiUrl   its documentation, or null when it has none
 * @param reviewUrl where a buyer leaves a review, or null when there is nowhere
 */
public record ProductLinks(String wikiUrl, String reviewUrl) {
    public static final ProductLinks NONE = new ProductLinks(null, null);
}
