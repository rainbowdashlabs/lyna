/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.nexus.entities.AssetXO;

import java.util.Arrays;
import java.util.Locale;
import java.util.stream.Collectors;

/**
 * What a downloaded jar is called, wherever it is downloaded from.
 *
 * <p>{@code <Product>-<version>.<extension>}, with the product's name in PascalCase and the version as
 * Nexus holds it. When the same version is offered in more than one of the product's download types -
 * a Paper and a Spigot build of 1.7.8 - the download type's name follows the version so the files can
 * be told apart. Otherwise a build that is not stable says which release type it is.
 *
 * <p>Deciding whether a version is offered more than once reads the other download types' version
 * lists, which are cached, so naming a file costs no call to Nexus.
 */
public final class DownloadFilename {
    private DownloadFilename() {}

    public static String of(Download download, AssetXO asset) {
        String version = asset.maven2().version();
        String name = pascalCase(download.product().name()) + "-" + version;
        String suffix = suffix(download, version);
        if (!suffix.isEmpty()) name += "-" + suffix;
        return name + "." + asset.maven2().extension();
    }

    private static String suffix(Download download, String version) {
        long carrying = download.product().downloads().downloads().stream()
                .filter(other -> other.latestAssets().stream()
                        .anyMatch(asset -> version.equals(asset.maven2().version())))
                .count();
        if (carrying > 1) return pascalCase(download.type().name());
        ReleaseType releaseType = download.type().releaseType();
        return releaseType == ReleaseType.STABLE
                ? ""
                : pascalCase(releaseType.name().toLowerCase(Locale.ROOT));
    }

    /**
     * {@code Schematic Brush Reborn} becomes {@code SchematicBrushReborn}: split on anything that is
     * not a letter or digit, and the first letter of each part raised. The rest of each part is left
     * as it was written, so {@code NashornJs} stays {@code NashornJs}.
     */
    static String pascalCase(String text) {
        return Arrays.stream(text.split("[^\\p{L}\\p{N}]+"))
                .filter(part -> !part.isEmpty())
                .map(part -> part.substring(0, 1).toUpperCase(Locale.ROOT) + part.substring(1))
                .collect(Collectors.joining());
    }
}
