/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.entity;

import de.chojo.lyna.feature.product.entity.Product;

/**
 * One licence: the key somebody bought, and the product it opens.
 *
 * <p>Data. Who holds it, who it is shared with and what it grants are questions for
 * {@code LicenseService} and {@code LicenseSharingService}, because the answers move Discord roles
 * as well as rows.
 */
public class License {
    private final Product product;
    private final String userIdentifier;
    private final int id;
    private final String key;

    /**
     * The holder's Discord id once something has looked it up, -1 while nothing has.
     *
     * <p>Held here so that a command asking twice does not read twice. Nothing but the service
     * should write it.
     */
    private long cachedOwner = -1;

    public License(Product product, String userIdentifier, int id, String key) {
        this.product = product;
        this.userIdentifier = userIdentifier;
        this.id = id;
        this.key = key;
    }

    public int id() {
        return id;
    }

    public String key() {
        return key;
    }

    /**
     * @return whoever the licence was issued against - an address for a purchase, whatever an
     *         operator wrote down otherwise
     */
    public String userIdentifier() {
        return userIdentifier;
    }

    public Product product() {
        return product;
    }

    public long cachedOwner() {
        return cachedOwner;
    }

    public void cachedOwner(long cachedOwner) {
        this.cachedOwner = cachedOwner;
    }
}
