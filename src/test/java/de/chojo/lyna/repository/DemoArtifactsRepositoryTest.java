/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.repository;

import de.chojo.lyna.data.access.DemoArtifacts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.sql.SQLException;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The record of what the demo seed made.
 *
 * <p>This is what a reset walks, so it has to be exact: something it forgets is left behind, and
 * something it records that the seed did not make is taken from somebody.
 */
class DemoArtifactsRepositoryTest extends RepositoryTestBase {

    @BeforeEach
    void clearArtifacts() throws SQLException {
        clear("demo_artifact");
    }

    @Test
    @DisplayName("Nothing is recorded until a seed runs")
    void startsEmpty() {
        assertFalse(demoArtifacts.seeded());
        assertTrue(demoArtifacts.of(DemoArtifacts.PRODUCT).isEmpty());
    }

    @Test
    @DisplayName("What was made is listed back, by kind and in the order it was made")
    void recordsAreListedByKind() {
        demoArtifacts.record(DemoArtifacts.PRODUCT, "1");
        demoArtifacts.record(DemoArtifacts.PRODUCT, "2");
        demoArtifacts.record(DemoArtifacts.ACCOUNT, "7");

        assertEquals(List.of("1", "2"), demoArtifacts.of(DemoArtifacts.PRODUCT));
        assertEquals(List.of("7"), demoArtifacts.of(DemoArtifacts.ACCOUNT));
        assertTrue(demoArtifacts.seeded());
    }

    @Test
    @DisplayName("Recording the same thing twice records it once")
    void recordIsIdempotent() {
        demoArtifacts.record(DemoArtifacts.PRODUCT, "1");
        demoArtifacts.record(DemoArtifacts.PRODUCT, "1");

        assertEquals(List.of("1"), demoArtifacts.of(DemoArtifacts.PRODUCT));
    }

    @Test
    @DisplayName("The same number under two kinds is two different things")
    void kindsAreSeparate() {
        demoArtifacts.record(DemoArtifacts.PRODUCT, "1");
        demoArtifacts.record(DemoArtifacts.ACCOUNT, "1");

        assertEquals(List.of("1"), demoArtifacts.of(DemoArtifacts.PRODUCT));
        assertEquals(List.of("1"), demoArtifacts.of(DemoArtifacts.ACCOUNT));
    }

    @Test
    @DisplayName("Clearing forgets everything, which is what a finished reset does")
    void clearForgetsEverything() {
        demoArtifacts.record(DemoArtifacts.PRODUCT, "1");
        demoArtifacts.record(DemoArtifacts.ACCOUNT, "7");

        demoArtifacts.clear();

        assertFalse(demoArtifacts.seeded());
        assertTrue(demoArtifacts.of(DemoArtifacts.PRODUCT).isEmpty());
        assertTrue(demoArtifacts.of(DemoArtifacts.ACCOUNT).isEmpty());
    }

    @Test
    @DisplayName("A kind nothing was recorded under lists nothing rather than failing")
    void unknownKindIsEmpty() {
        demoArtifacts.record(DemoArtifacts.PRODUCT, "1");

        assertTrue(demoArtifacts.of("something-else").isEmpty());
    }
}
