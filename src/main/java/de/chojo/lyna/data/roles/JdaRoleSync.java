/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.roles;

import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.feature.product.service.ProductRoleService;
import de.chojo.lyna.gateway.Gateway;

/**
 * Role cleanup through the gateway.
 */
public class JdaRoleSync implements RoleSync {
    private final ProductRoleService productRoles;
    private final Gateway gateway;

    public JdaRoleSync(Gateway gateway, ProductRoleService productRoles) {
        this.productRoles = productRoles;
        this.gateway = gateway;
    }

    @Override
    public void revoke(long guildId, long discordId, Product product) {
        gateway.member(guildId, discordId).ifPresent(member -> productRoles.revoke(product, member));
    }

    @Override
    public void revokeIfUnentitled(long guildId, long discordId, Product product) {
        gateway.member(guildId, discordId)
                .filter(member -> !productRoles.canAccess(product, member))
                .ifPresent(member -> productRoles.revoke(product, member));
    }
}
