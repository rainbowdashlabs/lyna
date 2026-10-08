/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.service;

import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.kiosk.service.ProductPageService.Page;
import de.chojo.lyna.feature.kiosk.service.ProductPageService.Source;
import de.chojo.lyna.feature.readme.service.ReadmeService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Whether a product page shows the product's description or its README.
 */
class ProductPageServiceTest {
    private static final String REPO = "https://github.com/owner/repo";

    private final ReadmeService readmes = mock(ReadmeService.class);
    private final ProductPageService pages = new ProductPageService(readmes);

    private static KioskProduct product(String url, String description, boolean readme) {
        return new KioskProduct(1, 2L, "P", url, null, true, null, description, false, readme);
    }

    @Test
    @DisplayName("A product asking for its README shows it, with a link to it")
    void readmeWhenAsked() {
        when(readmes.readme(REPO)).thenReturn(Optional.of(new ReadmeService.Readme("# R", "https://github.com/r")));

        assertEquals(
                new Page("# R", Source.README, "https://github.com/r"), pages.page(product(REPO, "own words", true)));
    }

    @Test
    @DisplayName(
            "A product saying nothing about itself shows its README anyway; one that says something keeps its words")
    void readmeByDefault() {
        when(readmes.readme(REPO)).thenReturn(Optional.of(new ReadmeService.Readme("# R", "https://github.com/r")));

        assertEquals(Source.README, pages.page(product(REPO, " ", false)).source());
        assertEquals(new Page("own words", Source.CUSTOM, null), pages.page(product(REPO, "own words", false)));
    }

    @Test
    @DisplayName("Without a GitHub repository, or when GitHub never answered, the description stands in")
    void fallback() {
        when(readmes.readme(anyString())).thenReturn(Optional.empty());

        assertEquals(new Page(null, Source.CUSTOM, null), pages.page(product("https://example.com", null, false)));
        assertEquals(new Page("own words", Source.CUSTOM, null), pages.page(product(REPO, "own words", true)));
        verify(readmes, never()).readme("https://example.com");
    }
}
