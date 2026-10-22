/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.product.repository.ProductFamilyRepository;

import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * Add-ons and the products they belong to.
 *
 * <p>A family is one level deep: a product is either an add-on of another, or has add-ons of its
 * own, or neither - never both. That keeps every list a single indent deep and leaves "similar"
 * meaning one thing: the other add-ons of the same product. A parent is only ever shown, never
 * required for a licence, a download or a role.
 */
@Singleton
public class ProductFamilyService {
    private final ProductFamilyRepository repository;

    @Inject
    public ProductFamilyService(ProductFamilyRepository repository) {
        this.repository = repository;
    }

    /**
     * Whether a product's parent may change, and why not when it may not.
     */
    public enum ParentChange {
        OK,
        NO_SUCH_PRODUCT,
        SELF,
        NO_SUCH_PARENT,
        PARENT_IS_ADDON,
        HAS_ADDONS
    }

    /**
     * The products around one product.
     *
     * @param parent  what it is an add-on of, or null
     * @param addons  its own add-ons, empty for an add-on
     * @param similar the other add-ons of its parent, empty for a product that is not an add-on
     */
    public record Family(KioskProduct parent, List<KioskProduct> addons, List<KioskProduct> similar) {}

    /**
     * Asks whether a product may become an add-on of another, or a product of its own again,
     * without changing anything.
     *
     * @param parentId the product it would become an add-on of, or null to make it stand on its own
     * @return {@link ParentChange#OK} when it may; otherwise why it may not
     */
    public ParentChange check(int productId, Integer parentId) {
        var product = repository.standing(productId);
        if (product.isEmpty()) return ParentChange.NO_SUCH_PRODUCT;
        if (parentId == null) return ParentChange.OK;
        if (parentId == productId) return ParentChange.SELF;
        var parent = repository.standing(parentId);
        if (parent.isEmpty() || parent.get().guildId() != product.get().guildId()) {
            return ParentChange.NO_SUCH_PARENT;
        }
        if (parent.get().parentId() != null) return ParentChange.PARENT_IS_ADDON;
        if (product.get().addons() > 0) return ParentChange.HAS_ADDONS;
        return ParentChange.OK;
    }

    /**
     * Makes a product an add-on of another, or a product of its own again, when {@link #check}
     * allows it.
     *
     * @param parentId the product it becomes an add-on of, or null to make it stand on its own
     * @return {@link ParentChange#OK} when it was changed; otherwise why it was not
     */
    public ParentChange parent(int productId, Integer parentId) {
        ParentChange verdict = check(productId, parentId);
        if (verdict == ParentChange.OK) repository.parent(productId, parentId);
        return verdict;
    }

    /**
     * @return every add-on's parent, by the add-on's id
     */
    public Map<Integer, Integer> parents() {
        return repository.parents();
    }

    /**
     * Finds a product's family within a catalogue already read.
     *
     * @param catalogue the products to look in, in the order they should be listed
     */
    public static Family family(KioskProduct product, List<KioskProduct> catalogue) {
        KioskProduct parent = product.parentId() == null
                ? null
                : catalogue.stream()
                        .filter(candidate -> candidate.id() == product.parentId())
                        .findFirst()
                        .orElse(null);
        List<KioskProduct> addons = catalogue.stream()
                .filter(candidate -> Objects.equals(candidate.parentId(), product.id()))
                .toList();
        List<KioskProduct> similar = product.parentId() == null
                ? List.of()
                : catalogue.stream()
                        .filter(candidate -> Objects.equals(candidate.parentId(), product.parentId()))
                        .filter(candidate -> candidate.id() != product.id())
                        .toList();
        return new Family(parent, addons, similar);
    }
}
