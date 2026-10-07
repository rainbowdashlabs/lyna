/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.butler.service;

import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * An update check as a plugin built against UpdateButler sends it.
 *
 * <p>Two shapes are in the wild. The old one is {@code ?id=5&version=1.2.3&devbuild=true}. The newer
 * one is {@code ?version1.2.3&id=5}: its format string lost the {@code =}, so the version arrives as
 * the name of a parameter with no value. That is also what tells it apart from Lyna's own check on the
 * same path, which always sends {@code version=}.
 */
public record ButlerCheckRequest(int butlerId, String version, boolean devBuild) {
    private static final String VERSION = "version";

    /**
     * @return the request in Butler's newer, malformed shape, or nothing when this is a well-formed
     *         query
     */
    public static Optional<ButlerCheckRequest> malformed(Map<String, List<String>> query) {
        if (query.containsKey(VERSION)) return Optional.empty();
        return query.keySet().stream()
                .filter(key -> key.startsWith(VERSION) && key.length() > VERSION.length())
                .findFirst()
                .flatMap(key -> of(query, key.substring(VERSION.length())));
    }

    /**
     * @return the request in Butler's old, well-formed shape
     */
    public static Optional<ButlerCheckRequest> wellFormed(Map<String, List<String>> query) {
        return first(query, VERSION).flatMap(version -> of(query, version));
    }

    private static Optional<ButlerCheckRequest> of(Map<String, List<String>> query, String version) {
        if (version.isBlank()) return Optional.empty();
        boolean devBuild = first(query, "devbuild").map(Boolean::parseBoolean).orElse(false);
        return first(query, "id").flatMap(ButlerCheckRequest::number)
                .map(id -> new ButlerCheckRequest(id, version, devBuild));
    }

    private static Optional<String> first(Map<String, List<String>> query, String key) {
        List<String> values = query.get(key);
        return values == null || values.isEmpty() ? Optional.empty() : Optional.ofNullable(values.getFirst());
    }

    private static Optional<Integer> number(String value) {
        try {
            return Optional.of(Integer.parseInt(value.strip()));
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }
}
