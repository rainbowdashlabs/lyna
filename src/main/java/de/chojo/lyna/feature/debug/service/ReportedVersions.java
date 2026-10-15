/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.util.Version;
import org.slf4j.Logger;

import java.time.Duration;
import java.util.Comparator;
import java.util.Optional;
import java.util.concurrent.ExecutionException;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Says whether the version a debug report came from is the latest, when the plugin is a product here.
 *
 * <p>The latest version is looked up on the track the reported one is on: a stable build is compared with stable
 * builds, a dev build with stable and dev builds, the newest build of each download as the update check finds it. Answers are kept for ten minutes, since every look asks Nexus.
 */
@Singleton
public class ReportedVersions {
    private static final Logger log = getLogger(ReportedVersions.class);

    private final ProductLookup products;
    private final Cache<String, Optional<String>> latest = CacheBuilder.newBuilder()
            .expireAfterWrite(Duration.ofMinutes(10))
            .maximumSize(1_000)
            .build();

    /**
     * @param version the latest version on the reported version's track
     * @param outdated whether the reported version is older than it
     */
    public record Latest(String version, boolean outdated) {}

    @Inject
    public ReportedVersions(ProductLookup products) {
        this.products = products;
    }

    /**
     * @return the latest version, or nothing when the plugin is no product here or Nexus does not answer
     */
    public Optional<Latest> latest(String pluginName, String reportedVersion) {
        Version reported = Version.parse(reportedVersion);
        String key = pluginName + "\u0000" + reported.type();
        try {
            return latest.get(key, () -> lookUp(pluginName, reported))
                    .map(version -> new Latest(version, Version.parse(version).isNewer(reported)));
        } catch (ExecutionException | RuntimeException e) {
            log.debug("Could not look up the latest version of {}", pluginName, e);
            return Optional.empty();
        }
    }

    private Optional<String> lookUp(String pluginName, Version reported) {
        return products.byPluginName(pluginName)
                .flatMap(product -> latestOf(product, reported))
                .map(Version::version);
    }

    private static Optional<Version> latestOf(Product product, Version reported) {
        return reported.type().descendants().stream()
                .flatMap(type -> product.downloads().byReleaseType(type).stream())
                .flatMap(download -> download.latestAssets().stream().limit(1))
                .map(asset -> Version.parse(asset.maven2().version()))
                .max(Comparator.naturalOrder());
    }
}
