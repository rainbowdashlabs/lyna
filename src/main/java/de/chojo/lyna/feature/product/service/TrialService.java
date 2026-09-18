/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductRepository;
import net.dv8tion.jda.api.entities.Member;

/**
 * Trials, which somebody gets one of per product.
 *
 * <p>Spending one is recorded rather than counted down: the row is the whole of the rule, and a
 * second attempt finds it there.
 */
@Singleton
public class TrialService {
    private final ProductRepository products;

    @Inject
    public TrialService(ProductRepository products) {
        this.products = products;
    }

    /**
     * @return whether this member still has their trial of the product to spend
     */
    public boolean hasTrial(Product product, Member member) {
        return products.trialUnspent(product.id(), member.getIdLong());
    }

    public void claimTrial(Product product, Member member) {
        products.spendTrial(product.id(), member.getIdLong());
    }
}
