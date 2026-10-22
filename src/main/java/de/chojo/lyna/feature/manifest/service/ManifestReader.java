/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.feature.manifest.entity.ManifestDependency;
import de.chojo.lyna.feature.manifest.entity.ManifestLink;
import de.chojo.lyna.feature.manifest.entity.PluginManifest;

import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Reads a plugin manifest as the build published it.
 *
 * <p>The manifest comes from a build Lyna did not run, so nothing in it is taken on trust: an unknown
 * schema is not read at all, a dependency without a name is left out, and a link that is not one Lyna
 * would show is dropped.
 */
public final class ManifestReader {
    private static final ObjectMapper JSON = new ObjectMapper();

    private ManifestReader() {}

    /**
     * @return the manifest, or nothing when it is not JSON or not of {@link PluginManifest#SCHEMA}
     */
    public static Optional<PluginManifest> read(String json) {
        try {
            return read(JSON.readTree(json));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static Optional<PluginManifest> read(JsonNode root) {
        if (root == null || !root.isObject() || root.path("schema").asInt(-1) != PluginManifest.SCHEMA) {
            return Optional.empty();
        }
        JsonNode minecraft = root.path("minecraft");
        return Optional.of(new PluginManifest(
                text(root.get("name")),
                text(root.get("version")),
                texts(root.path("platforms")),
                text(minecraft.get("min")),
                text(minecraft.get("max")),
                root.path("java").canConvertToInt() && root.path("java").isNumber()
                        ? root.path("java").asInt()
                        : null,
                dependencies(root.path("dependencies")),
                extra(root.path("extra"))));
    }

    private static List<ManifestDependency> dependencies(JsonNode array) {
        List<ManifestDependency> dependencies = new ArrayList<>();
        for (JsonNode dependency : array) {
            String name = text(dependency.get("name"));
            if (name == null) continue;
            List<ManifestLink> links = new ArrayList<>();
            for (JsonNode link : dependency.path("links")) {
                ManifestLink.of(text(link.get("type")), text(link.get("id")), text(link.get("url")))
                        .ifPresent(links::add);
            }
            dependencies.add(new ManifestDependency(
                    name,
                    dependency.path("required").asBoolean(false),
                    text(dependency.get("min")),
                    text(dependency.get("max")),
                    List.copyOf(links)));
        }
        return List.copyOf(dependencies);
    }

    private static Map<String, String> extra(JsonNode object) {
        Map<String, String> extra = new LinkedHashMap<>();
        object.properties().forEach(entry -> {
            String value = text(entry.getValue());
            if (value != null) extra.put(entry.getKey(), value);
        });
        return extra;
    }

    private static List<String> texts(JsonNode array) {
        List<String> texts = new ArrayList<>();
        for (JsonNode node : array) {
            String text = text(node);
            if (text != null) texts.add(text);
        }
        return List.copyOf(texts);
    }

    private static String text(JsonNode node) {
        if (node == null || !node.isValueNode() || node.isNull()) return null;
        String text = node.asText().strip();
        return text.isEmpty() ? null : text;
    }
}
