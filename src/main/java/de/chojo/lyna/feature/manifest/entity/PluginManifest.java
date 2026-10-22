/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.entity;

import java.util.List;
import java.util.Map;

/**
 * What a build says it needs, as published beside its jar.
 *
 * @param minecraftMin the oldest supported Minecraft version, inclusive, or null
 * @param minecraftMax the newest supported Minecraft version, inclusive, or null for no known bound
 * @param java         the Java version it needs, or null
 */
public record PluginManifest(
        String name,
        String version,
        List<String> platforms,
        String minecraftMin,
        String minecraftMax,
        Integer java,
        List<ManifestDependency> dependencies,
        Map<String, String> extra) {
    /** The manifest format Lyna reads. A manifest of another schema is not read at all. */
    public static final int SCHEMA = 1;

    /**
     * Whether the build runs on a Minecraft version.
     *
     * <p>A bound covers every version it is a prefix of: a maximum of {@code 1.21} covers {@code 1.21.4},
     * and a minimum of {@code 1.20} covers {@code 1.20}. A missing bound covers everything on its side.
     */
    public boolean supports(String minecraft) {
        if (minecraftMin != null && compareAtPrecisionOf(minecraft, minecraftMin) < 0) return false;
        return minecraftMax == null || compareAtPrecisionOf(minecraft, minecraftMax) <= 0;
    }

    /**
     * Compares {@code version} with {@code bound} over as many numeric parts as the bound has.
     */
    static int compareAtPrecisionOf(String version, String bound) {
        String[] parts = version.split("\\.");
        String[] limits = bound.split("\\.");
        for (int i = 0; i < limits.length; i++) {
            int compare = Integer.compare(number(i < parts.length ? parts[i] : "0"), number(limits[i]));
            if (compare != 0) return compare;
        }
        return 0;
    }

    private static int number(String part) {
        String digits = part.replaceAll("\\D.*", "");
        return digits.isEmpty() ? 0 : Integer.parseInt(digits);
    }
}
