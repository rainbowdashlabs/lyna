/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.entity;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Where a dependency is found.
 *
 * <p>A Modrinth, SpigotMC or Hangar link is rebuilt from its id, so a manifest cannot pass a link to
 * somewhere else off as one of those sites. Any other link has to be an http or https address.
 *
 * @param type {@code modrinth}, {@code spigot}, {@code hangar} or {@code url}
 * @param id   the project on that site, or null for a plain address
 */
public record ManifestLink(String type, String id, String url) {
    private static final Pattern SLUG = Pattern.compile("[A-Za-z0-9_.-]+");
    private static final Pattern SPIGOT = Pattern.compile("[1-9][0-9]*");
    private static final Pattern HANGAR = Pattern.compile("[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+");

    /**
     * @return the link as it is shown, or nothing when its id or address is not one Lyna will link to
     */
    public static Optional<ManifestLink> of(String type, String id, String url) {
        if (type == null) return Optional.empty();
        return switch (type) {
            case "modrinth" ->
                matching(SLUG, id).map(slug -> new ManifestLink(type, slug, "https://modrinth.com/plugin/" + slug));
            case "spigot" ->
                matching(SPIGOT, id)
                        .map(resource -> new ManifestLink(
                                type, resource, "https://www.spigotmc.org/resources/" + resource + "/"));
            case "hangar" ->
                matching(HANGAR, id)
                        .map(project -> new ManifestLink(type, project, "https://hangar.papermc.io/" + project));
            default -> web(url).map(address -> new ManifestLink("url", null, address));
        };
    }

    private static Optional<String> matching(Pattern pattern, String id) {
        return id != null && pattern.matcher(id).matches() ? Optional.of(id) : Optional.empty();
    }

    private static Optional<String> web(String url) {
        if (url == null) return Optional.empty();
        try {
            URI uri = new URI(url);
            boolean web = ("http".equals(uri.getScheme()) || "https".equals(uri.getScheme()))
                    && uri.getHost() != null
                    && !uri.getHost().isBlank();
            return web ? Optional.of(url) : Optional.empty();
        } catch (URISyntaxException e) {
            return Optional.empty();
        }
    }
}
