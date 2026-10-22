/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.entity;

import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * A version's manifest as a page shows it: what it says, with each dependency that is also a product
 * of the same guild pointed at that product.
 */
public record ManifestView(
        List<String> platforms,
        String minecraftMin,
        String minecraftMax,
        Integer java,
        List<DependencyView> dependencies,
        Map<String, String> extra) {
    /**
     * @param productId the product of the same guild this dependency is, or null when it is none
     */
    public record DependencyView(
            String name, boolean required, String min, String max, List<ManifestLink> links, Integer productId) {}

    /**
     * @param productByName the id of the guild's product of a name, or null when there is none
     */
    public static ManifestView of(PluginManifest manifest, Function<String, Integer> productByName) {
        return new ManifestView(
                manifest.platforms(),
                manifest.minecraftMin(),
                manifest.minecraftMax(),
                manifest.java(),
                manifest.dependencies().stream()
                        .map(dependency -> new DependencyView(
                                dependency.name(),
                                dependency.required(),
                                dependency.min(),
                                dependency.max(),
                                dependency.links(),
                                productByName.apply(dependency.name())))
                        .toList(),
                manifest.extra());
    }
}
