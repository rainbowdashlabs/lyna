/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.service;

import de.chojo.lyna.feature.download.entity.Download;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static de.chojo.lyna.feature.download.service.ProductFixture.asset;
import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * What a downloaded jar is called.
 */
class DownloadFilenameTest {

    @Test
    @DisplayName("A stable build only one download type carries is the product and the version")
    void stableAndUnsharedIsBare() {
        ProductFixture fixture = new ProductFixture("Blood Night");
        Download stable = fixture.download(1, "Spigot Stable", null, ReleaseType.STABLE, "1.2.0");
        fixture.download(2, "Spigot Dev", null, ReleaseType.DEV, "1.2.1-SNAPSHOT");

        assertEquals("BloodNight-1.2.0.jar", DownloadFilename.of(stable, asset("1.2.0")));
    }

    @Test
    @DisplayName("A version several download types carry says which one this is")
    void sharedVersionNamesItsDownloadType() {
        ProductFixture fixture = new ProductFixture("Schematic Brush Reborn");
        Download paper = fixture.download(1, "Paper Stable", null, ReleaseType.STABLE, "1.7.8");
        Download spigot = fixture.download(2, "Spigot Stable", null, ReleaseType.STABLE, "1.7.8");

        assertEquals("SchematicBrushReborn-1.7.8-PaperStable.jar", DownloadFilename.of(paper, asset("1.7.8")));
        assertEquals("SchematicBrushReborn-1.7.8-SpigotStable.jar", DownloadFilename.of(spigot, asset("1.7.8")));
    }

    @Test
    @DisplayName("A dev or snapshot build says so, even when nothing else carries it")
    void unstableSaysItsReleaseType() {
        ProductFixture fixture = new ProductFixture("Blood Night");
        Download dev = fixture.download(1, "Spigot Dev", null, ReleaseType.DEV, "1.2.1-SNAPSHOT");
        Download snapshot = fixture.download(2, "Spigot Snapshot", null, ReleaseType.SNAPSHOT, "1.2.2-SNAPSHOT");

        assertEquals("BloodNight-1.2.1-SNAPSHOT-Dev.jar", DownloadFilename.of(dev, asset("1.2.1-SNAPSHOT")));
        assertEquals("BloodNight-1.2.2-SNAPSHOT-Snapshot.jar", DownloadFilename.of(snapshot, asset("1.2.2-SNAPSHOT")));
    }

    @Test
    @DisplayName("A shared dev build is named once, by its download type, not twice over")
    void sharedDevBuildIsNotDoubled() {
        ProductFixture fixture = new ProductFixture("Schematic Brush Reborn");
        Download paper = fixture.download(1, "Paper Dev", null, ReleaseType.DEV, "1.7.9-SNAPSHOT");
        fixture.download(2, "Spigot Dev", null, ReleaseType.DEV, "1.7.9-SNAPSHOT");

        assertEquals(
                "SchematicBrushReborn-1.7.9-SNAPSHOT-PaperDev.jar",
                DownloadFilename.of(paper, asset("1.7.9-SNAPSHOT")));
    }

    @Test
    @DisplayName("Names are PascalCase, with what was already capitalised left alone")
    void pascalCase() {
        assertEquals("SchematicBrushReborn", DownloadFilename.pascalCase("Schematic Brush Reborn"));
        assertEquals("NashornJs", DownloadFilename.pascalCase("NashornJs"));
        assertEquals("PickMeUp", DownloadFilename.pascalCase("pick-me up"));
        assertEquals("PaperStableLegacy", DownloadFilename.pascalCase("  Paper  Stable / Legacy "));
    }
}
