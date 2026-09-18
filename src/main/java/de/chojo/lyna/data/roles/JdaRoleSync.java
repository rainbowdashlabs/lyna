/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.roles;

import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.gateway.Gateway;

/**
 * Role cleanup through the gateway.
 */
public class JdaRoleSync implements RoleSync {
    private final Gateway gateway;

    public JdaRoleSync(Gateway gateway) {
        this.gateway = gateway;
    }

    @Override
    public void revoke(long guildId, long discordId, Product product) {
        gateway.member(guildId, discordId).ifPresent(product::revoke);
    }

    @Override
    public void revokeIfUnentitled(long guildId, long discordId, Product product) {
        gateway.member(guildId, discordId)
                .filter(member -> !product.canAccess(member))
                .ifPresent(product::revoke);
    }
}
