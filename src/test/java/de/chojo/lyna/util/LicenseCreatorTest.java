/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.util;

import de.chojo.lyna.feature.product.entity.Product;
import org.junit.jupiter.api.Test;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class LicenseCreatorTest {

    @Test
    void create() {
        Product product = mock(Product.class);
        when(product.id()).thenReturn(1);
        String license = LicenseCreator.create(1234, product, "854264");
        System.out.println(license);
    }
}
