/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.service;

import de.chojo.lyna.feature.manifest.entity.ManifestDependency;
import de.chojo.lyna.feature.manifest.entity.ManifestLink;
import de.chojo.lyna.feature.manifest.entity.PluginManifest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Reading a manifest a build published, which Lyna does not take on trust.
 */
class ManifestReaderTest {
    @Test
    @DisplayName("A manifest as the Gradle plugin writes it is read whole")
    void whole() {
        String json = """
                {
                  "schema": 1,
                  "name": "BloodNight",
                  "version": "1.4.0",
                  "platforms": ["paper", "folia"],
                  "minecraft": {"min": "1.20.4", "max": "1.21.4"},
                  "java": 21,
                  "dependencies": [
                    {"name": "PlaceholderAPI", "required": false, "min": "2.11.0", "links": [
                      {"type": "modrinth", "id": "placeholderapi", "url": "https://modrinth.com/plugin/placeholderapi"},
                      {"type": "spigot", "id": "6245", "url": "https://www.spigotmc.org/resources/6245/"},
                      {"type": "hangar", "id": "HelpChat/PlaceholderAPI", "url": "https://hangar.papermc.io/HelpChat/PlaceholderAPI"},
                      {"type": "url", "url": "https://github.com/PlaceholderAPI/PlaceholderAPI"}
                    ]},
                    {"name": "Vault", "required": true}
                  ],
                  "extra": {"discord": "https://discord.gg/x"}
                }
                """;

        PluginManifest manifest = ManifestReader.read(json).orElseThrow();

        assertEquals(
                new PluginManifest(
                        "BloodNight",
                        "1.4.0",
                        List.of("paper", "folia"),
                        "1.20.4",
                        "1.21.4",
                        21,
                        List.of(
                                new ManifestDependency(
                                        "PlaceholderAPI",
                                        false,
                                        "2.11.0",
                                        null,
                                        List.of(
                                                new ManifestLink(
                                                        "modrinth",
                                                        "placeholderapi",
                                                        "https://modrinth.com/plugin/placeholderapi"),
                                                new ManifestLink(
                                                        "spigot", "6245", "https://www.spigotmc.org/resources/6245/"),
                                                new ManifestLink(
                                                        "hangar",
                                                        "HelpChat/PlaceholderAPI",
                                                        "https://hangar.papermc.io/HelpChat/PlaceholderAPI"),
                                                new ManifestLink(
                                                        "url",
                                                        null,
                                                        "https://github.com/PlaceholderAPI/PlaceholderAPI"))),
                                new ManifestDependency("Vault", true, null, null, List.of())),
                        Map.of("discord", "https://discord.gg/x")),
                manifest);
    }

    @Test
    @DisplayName("A shortcut's address is rebuilt from its id, and links Lyna would not show are dropped")
    void linksAreNotTrusted() {
        String json = """
                {"schema": 1, "dependencies": [{"name": "X", "required": true, "links": [
                  {"type": "modrinth", "id": "real", "url": "https://evil.example/phish"},
                  {"type": "modrinth", "id": "../evil"},
                  {"type": "spigot", "id": "0"},
                  {"type": "hangar", "id": "no-owner"},
                  {"type": "url", "url": "javascript:alert(1)"},
                  {"type": "url", "url": "https:///nohost"},
                  {"type": "url", "url": "not a url"},
                  {"type": "something-new", "url": "https://example.com/new"},
                  {"url": "https://example.com/untyped"}
                ]}]}
                """;

        List<ManifestLink> links = ManifestReader.read(json)
                .orElseThrow()
                .dependencies()
                .getFirst()
                .links();

        assertEquals(
                List.of(
                        new ManifestLink("modrinth", "real", "https://modrinth.com/plugin/real"),
                        new ManifestLink("url", null, "https://example.com/new")),
                links);
    }

    @Test
    @DisplayName("Not JSON, not an object or another schema is not read; odd fields are left out")
    void refusals() {
        assertTrue(ManifestReader.read("not json").isEmpty());
        assertTrue(ManifestReader.read("[1]").isEmpty());
        assertTrue(ManifestReader.read("{\"schema\": 2}").isEmpty());
        assertTrue(ManifestReader.read("{}").isEmpty());

        PluginManifest odd = ManifestReader.read("""
                {"schema": 1, "name": " ", "java": "21", "platforms": ["paper", {"x": 1}, null],
                 "minecraft": {"min": {"a": 1}}, "dependencies": [{"required": true}, {"name": "Kept"}],
                 "extra": {"n": 1, "o": {"x": 1}}}
                """).orElseThrow();
        assertEquals(
                new PluginManifest(
                        null,
                        null,
                        List.of("paper"),
                        null,
                        null,
                        null,
                        List.of(new ManifestDependency("Kept", false, null, null, List.of())),
                        Map.of("n", "1")),
                odd);
    }
}
