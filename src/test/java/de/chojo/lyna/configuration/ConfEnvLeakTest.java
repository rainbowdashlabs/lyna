/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * That nothing the environment supplied reaches the file.
 *
 * <p>Ocular applies the overrides into the object it would serialise, so writing that object back is
 * how a password lands on disk. A call to {@code save()} added during other work did exactly that to
 * a running instance, which is why the method now refuses rather than relying on nobody calling it.
 */
class ConfEnvLeakTest {

    @Test
    @DisplayName("Saving is refused, because it would write what the environment supplied")
    void savingIsRefused(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), "database:\n  host: \"h\"\n");
        Conf conf = new Conf(directory);
        conf.main();

        assertThrows(UnsupportedOperationException.class, conf::save);
        assertThrows(UnsupportedOperationException.class, () -> conf.save(Conf.CONFIG));
    }

    @Test
    @DisplayName("Reading, twice over, leaves the override where it was")
    void readingDoesNotPersistAnOverride(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), "database:\n  host: \"h\"\n");
        System.setProperty("auth.jwtSecret", "a-secret-from-outside");
        try {
            new Conf(directory).main();
            new Conf(directory).main();

            assertFalse(
                    Files.readString(directory.resolve("config.yaml")).contains("a-secret-from-outside"),
                    "the file must not gain what the environment supplied");
        } finally {
            System.clearProperty("auth.jwtSecret");
        }
    }
}
