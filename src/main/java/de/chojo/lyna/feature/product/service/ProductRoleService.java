/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.data.dao.downloadtype.ReleaseType;
import de.chojo.lyna.data.dao.products.Product;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import net.dv8tion.jda.api.entities.Member;
import net.dv8tion.jda.api.entities.Role;

import java.util.EnumSet;
import java.util.List;
import java.util.Set;

/**
 * What a member may do with a product.
 *
 * <p>Two things decide it and neither is a row on its own: a licence somebody holds, and the Discord
 * roles they wear. A free product answers yes to both without asking, which is why the check is here
 * rather than in a query.
 */
@Singleton
public class ProductRoleService {
    private final ProductRepository products;

    @Inject
    public ProductRoleService(ProductRepository products) {
        this.products = products;
    }

    /**
     * @return whether the member may have the product at all
     */
    public boolean canAccess(Product product, Member member) {
        if (product.free()) return true;
        return product.products().licenseGuild().user(member).canAccess(product);
    }

    /**
     * @return whether the member may download something, which needs a release type they can reach
     */
    public boolean canDownload(Product product, Member member) {
        if (product.free()) return true;
        return !availableReleaseTypes(product, member).isEmpty();
    }

    /**
     * What the member may download, gathered from the licences they hold and the roles they wear.
     *
     * @return the release types they can reach, empty when they can reach none
     */
    public Set<ReleaseType> availableReleaseTypes(Product product, Member member) {
        if (product.free()) {
            return Set.of(ReleaseType.values());
        }
        List<ReleaseType> byHolder = products.accessByHolder(product.id(), member.getIdLong());
        List<ReleaseType> byRole = products.accessByRoles(
                product.id(), member.getRoles().stream().map(Role::getIdLong).toList());
        var result = EnumSet.noneOf(ReleaseType.class);
        result.addAll(byHolder);
        result.addAll(byRole);
        return result;
    }

    /**
     * Gives the member the product's role, unless they already wear it.
     */
    public void assign(Product product, Member member) {
        Role role = member.getGuild().getRoleById(product.role());
        if (role != null && !member.getRoles().contains(role)) {
            member.getGuild().addRoleToMember(member, role).queue();
        }
    }

    /**
     * Takes the product's role back, if the member wears it.
     */
    public void revoke(Product product, Member member) {
        Role role = member.getGuild().getRoleById(product.role());
        if (role != null && member.getRoles().contains(role)) {
            member.getGuild().removeRoleFromMember(member, role).queue();
        }
    }
}
