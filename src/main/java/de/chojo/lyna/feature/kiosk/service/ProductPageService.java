/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.kiosk.entity.KioskProduct;
import de.chojo.lyna.feature.readme.service.ReadmeLinks;
import de.chojo.lyna.feature.readme.service.ReadmeService;

import java.util.Optional;

/**
 * What a product's page says: its own description, or the README of the GitHub repository it names.
 *
 * <p>The README is shown when the product asks for it, and also when the product says nothing about
 * itself and names a GitHub repository - an empty page helps nobody when the README is right there.
 * When GitHub has never answered for the repository, the description stands in.
 */
@Singleton
public class ProductPageService {
    private final ReadmeService readmes;

    public enum Source {
        CUSTOM,
        README
    }

    /**
     * @param markdown  what the page shows, or null when there is nothing to show
     * @param source    where it came from
     * @param readmeUrl the README's page on GitHub, when the page shows it
     */
    public record Page(String markdown, Source source, String readmeUrl) {}

    @Inject
    public ProductPageService(ReadmeService readmes) {
        this.readmes = readmes;
    }

    public Page page(KioskProduct product) {
        boolean blank = product.description() == null || product.description().isBlank();
        boolean wantsReadme = product.pageReadme()
                || (blank && ReadmeLinks.repository(product.url()).isPresent());
        Optional<ReadmeService.Readme> readme = wantsReadme ? readmes.readme(product.url()) : Optional.empty();
        return readme.map(found -> new Page(found.markdown(), Source.README, found.url()))
                .orElseGet(() -> new Page(product.description(), Source.CUSTOM, null));
    }
}
