/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import com.google.inject.Inject;
import de.chojo.lyna.feature.account.entity.AccountIdentity;
import de.chojo.lyna.feature.account.repository.AccountLicenseRepository;
import de.chojo.lyna.feature.account.service.AccountLinkService;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import de.chojo.lyna.gateway.Gateway;
import net.dv8tion.jda.api.entities.Role;

import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

/**
 * What a Lyna account may download, by the same rule the bot applies to a member.
 *
 * <p>Two things open a product: a licence the account holds or was shared, and a Discord role its
 * linked Discord account wears in the product's guild. The roles are asked of the gateway, so an
 * account without a linked Discord, a member who left the guild, or an instance running without the
 * bot is left with what its licences carry.
 */
public class DownloadAccessService {
    private final AccountLicenseRepository licenses;
    private final AccountLinkService links;
    private final ProductRepository products;
    private final Gateway gateway;

    @Inject
    public DownloadAccessService(
            AccountLicenseRepository licenses, AccountLinkService links, ProductRepository products, Gateway gateway) {
        this.licenses = licenses;
        this.links = links;
        this.products = products;
        this.gateway = gateway;
    }

    /**
     * @return the release types of the product the account may download, as the names the RELEASE_TYPE enum
     *     uses
     */
    public Set<String> releaseTypes(int accountId, int productId) {
        Set<String> types = new HashSet<>(licenses.releaseTypes(accountId, productId));
        products.accessByRoles(productId, roles(accountId)).stream()
                .map(ReleaseType::name)
                .forEach(types::add);
        return Set.copyOf(types);
    }

    /**
     * @return the products the account may download something of
     */
    public Set<Integer> entitledProductIds(int accountId) {
        Set<Integer> entitled = new HashSet<>(licenses.entitledProductIds(accountId));
        entitled.addAll(products.productsByRoles(roles(accountId)));
        return Set.copyOf(entitled);
    }

    private List<Long> roles(int accountId) {
        Optional<Long> discordId = links.discordIdentity(accountId).map(AccountIdentity::externalIdAsLong);
        if (discordId.isEmpty()) return List.of();
        return products.guildsWithRoleAccess().stream()
                .flatMap(guild -> gateway.member(guild, discordId.get()).stream())
                .flatMap(member -> member.getRoles().stream())
                .map(Role::getIdLong)
                .toList();
    }
}
