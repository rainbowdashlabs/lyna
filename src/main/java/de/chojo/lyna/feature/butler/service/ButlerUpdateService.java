/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.butler.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.butler.repository.ButlerApplicationRepository;
import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.util.Version;
import de.chojo.nexus.entities.AssetXO;

import java.util.Comparator;
import java.util.Optional;
import java.util.stream.Stream;

/**
 * Answers the update checks and downloads of plugins built against UpdateButler.
 *
 * <p>Those plugins compare the sha256 they are given with the file they then download and throw the
 * file away on a mismatch, so the hash is Nexus' own and the download must be the unaltered asset.
 *
 * <p>Butler had two tracks, stable and dev. A plugin sends {@code devbuild} for the second, but the
 * newer clients send nothing at all, so a running version that is itself a dev or snapshot build
 * also follows its own track. Every track includes the ones below it.
 */
@Singleton
public class ButlerUpdateService {
    private static final Comparator<AssetXO> NEWEST = Comparator
            .comparing((AssetXO asset) -> Version.parse(asset.maven2().version()))
            .thenComparing(AssetXO::lastModified);

    private final ButlerApplicationRepository applications;
    private final ProductLookup products;

    /**
     * Field names are what the deployed clients read with Gson; they cannot change.
     *
     * @param hash the sha256 of the latest build
     */
    public record CheckAnswer(boolean newVersionAvailable, String latestVersion, String hash) {
        public static final CheckAnswer UNKNOWN = new CheckAnswer(false, null, null);
    }

    public record Build(Download download, AssetXO asset) {}

    @Inject
    public ButlerUpdateService(ButlerApplicationRepository applications, ProductLookup products) {
        this.applications = applications;
        this.products = products;
    }

    public Optional<Product> product(int butlerId) {
        return applications.productFor(butlerId).flatMap(products::byId);
    }

    /**
     * @return what to tell a plugin running this version, or nothing when the product has no build
     *         on its track
     */
    public Optional<CheckAnswer> check(Product product, String runningVersion, boolean devBuild) {
        Version running = Version.parse(runningVersion);
        return latest(product, track(running, devBuild)).map(asset -> new CheckAnswer(
                Version.parse(asset.maven2().version()).isNewer(running),
                asset.maven2().version(),
                asset.checksum() == null ? null : asset.checksum().sha256()));
    }

    /**
     * The build of this version, looked for in stable downloads first.
     */
    public Optional<Build> build(Product product, String version) {
        return Stream.of(ReleaseType.values())
                .flatMap(type -> product.downloads().byReleaseType(type).stream())
                .flatMap(download -> download.assetByVersion(version).map(asset -> new Build(download, asset)).stream())
                .findFirst();
    }

    private static ReleaseType track(Version running, boolean devBuild) {
        if (devBuild && running.type() == ReleaseType.STABLE) return ReleaseType.DEV;
        return running.type();
    }

    private static Optional<AssetXO> latest(Product product, ReleaseType track) {
        return track.descendants().stream()
                .flatMap(type -> product.downloads().byReleaseType(type).stream())
                .flatMap(download -> download.latestAssets().stream())
                .max(NEWEST);
    }
}
