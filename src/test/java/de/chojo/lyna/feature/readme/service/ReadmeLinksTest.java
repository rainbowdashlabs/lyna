/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.readme.service;

import de.chojo.lyna.feature.readme.service.ReadmeLinks.Repository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which project addresses name a GitHub repository, and where a README's links lead off GitHub.
 */
class ReadmeLinksTest {
    private static final Repository REPO = new Repository("eldoriarpg", "BloodNight");

    @Test
    @DisplayName("A repository's page names it, with or without .git or a trailing slash; anything else does not")
    void repository() {
        assertEquals(Optional.of(REPO), ReadmeLinks.repository("https://github.com/eldoriarpg/BloodNight"));
        assertEquals(Optional.of(REPO), ReadmeLinks.repository("https://github.com/eldoriarpg/BloodNight.git"));
        assertEquals(Optional.of(REPO), ReadmeLinks.repository(" https://www.github.com/eldoriarpg/BloodNight/ "));
        assertTrue(ReadmeLinks.repository("https://github.com/eldoriarpg/BloodNight/tree/main")
                .isEmpty());
        assertTrue(ReadmeLinks.repository("https://example.com/eldoriarpg/BloodNight")
                .isEmpty());
        assertTrue(ReadmeLinks.repository(null).isEmpty());
    }

    @Test
    @DisplayName("Relative images go to the raw file, relative links to the file's page, both from the README's folder")
    void relativeLinks() {
        String rewritten = ReadmeLinks.absolute(
                "![logo](img/logo.png) [setup](./setup.md) <img src=\"a.png\"> <a href='b.md'>b</a>",
                REPO,
                "docs/README.md");

        assertEquals(
                "![logo](https://raw.githubusercontent.com/eldoriarpg/BloodNight/HEAD/docs/img/logo.png) "
                        + "[setup](https://github.com/eldoriarpg/BloodNight/blob/HEAD/docs/setup.md) "
                        + "<img src=\"https://raw.githubusercontent.com/eldoriarpg/BloodNight/HEAD/docs/a.png\"> "
                        + "<a href='https://github.com/eldoriarpg/BloodNight/blob/HEAD/docs/b.md'>b</a>",
                rewritten);
    }

    @Test
    @DisplayName("A link from the root resolves against the repository; absolute links and anchors stay")
    void absoluteLinksStay() {
        String rewritten = ReadmeLinks.absolute(
                "[root](/LICENSE) [web](https://spigotmc.org) [mail](mailto:a@b.c) [top](#usage) ![b](//cdn/x.png)",
                REPO,
                "README.md");

        assertEquals(
                "[root](https://github.com/eldoriarpg/BloodNight/blob/HEAD/LICENSE) [web](https://spigotmc.org) "
                        + "[mail](mailto:a@b.c) [top](#usage) ![b](//cdn/x.png)",
                rewritten);
    }

    @Test
    @DisplayName("Download links to the releases and the plugin hosts lead to Lyna; badges and other links stay")
    void downloadsToLyna() {
        String readme =
                "[![spigot](https://img.shields.io/x.svg)](https://www.spigotmc.org/resources/blood-night.85095/) "
                        + "[Releases](https://github.com/eldoriarpg/BloodNight/releases/latest) "
                        + "<a href=\"https://modrinth.com/plugin/bloodnight\">Modrinth</a> "
                        + "[Wiki](https://github.com/eldoriarpg/BloodNight/wiki) [Other](https://github.com/someone/else/releases)";

        assertEquals(
                "[![spigot](https://img.shields.io/x.svg)](/products/4?download=1) "
                        + "[Releases](/products/4?download=1) "
                        + "<a href=\"/products/4?download=1\">Modrinth</a> "
                        + "[Wiki](https://github.com/eldoriarpg/BloodNight/wiki) [Other](https://github.com/someone/else/releases)",
                ReadmeLinks.downloadsTo(readme, REPO, "/products/4?download=1"));
    }
}
