/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.entity;

/**
 * Where a product is sold on BuiltByBit: a resource, or one of its add-ons.
 *
 * @param resourceId the resource
 * @param addonId    the add-on of that resource, or {@link #NO_ADDON} for the resource itself
 */
public record BuiltByBitListing(long resourceId, long addonId) {
    public static final long NO_ADDON = 0L;

    public static BuiltByBitListing resource(long resourceId) {
        return new BuiltByBitListing(resourceId, NO_ADDON);
    }

    public boolean isAddon() {
        return addonId != NO_ADDON;
    }

    /**
     * @return the page a buyer opens to buy it; an add-on is bought on its resource's page
     */
    public String shopUrl() {
        return "https://builtbybit.com/resources/%d/".formatted(resourceId);
    }
}
