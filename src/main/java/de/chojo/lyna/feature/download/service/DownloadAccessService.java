/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.repository.AccountRoleRepository;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.product.repository.ProductRepository;

import java.util.HashSet;
import java.util.Set;

/**
 * What a Lyna account may download, by the same rule the bot applies to a member.
 *
 * <p>Two things open a product: a licence the account holds or was shared, and a role the account
 * wears. Roles are read from what {@link de.chojo.lyna.feature.account.service.AccountRoleSync} stored,
 * so answering never waits on Discord.
 */
public class DownloadAccessService {
    private final AccountLicenseRepository licenses;
    private final AccountRoleRepository roles;
    private final ProductRepository products;

    @Inject
    public DownloadAccessService(
            AccountLicenseRepository licenses, AccountRoleRepository roles, ProductRepository products) {
        this.licenses = licenses;
        this.roles = roles;
        this.products = products;
    }

    /**
     * @return the release types of the product the account may download, as the names the RELEASE_TYPE enum
     *     uses
     */
    public Set<String> releaseTypes(int accountId, int productId) {
        Set<String> types = new HashSet<>(licenses.releaseTypes(accountId, productId));
        products.accessByRoles(productId, roles.roles(accountId)).stream()
                .map(ReleaseType::name)
                .forEach(types::add);
        return Set.copyOf(types);
    }

    /**
     * @return the products the account may download something of
     */
    public Set<Integer> entitledProductIds(int accountId) {
        Set<Integer> entitled = new HashSet<>(licenses.entitledProductIds(accountId));
        entitled.addAll(products.productsByRoles(roles.roles(accountId)));
        return Set.copyOf(entitled);
    }
}
