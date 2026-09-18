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

/**
 * What reading the configuration does to the file.
 *
 * <p>Reading it adds the settings a newer version understands and changes nothing else. Saving it
 * would rewrite the whole thing from the loaded object, which is why nothing does - not because a
 * value supplied from the environment must not be written down, which is allowed.
 */
class ConfEnvLeakTest {

    @Test
    @DisplayName("Reading, twice over, leaves what an override supplied out of the file")
    void readingDoesNotPersistAnOverride(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), "database:\n  host: \"h\"\n");
        System.setProperty("auth.jwtSecret", "a-secret-from-outside");
        try {
            new Conf(directory).main();
            new Conf(directory).main();

            assertFalse(
                    Files.readString(directory.resolve("config.yaml")).contains("a-secret-from-outside"),
                    "reading adds the settings, not the values behind them");
        } finally {
            System.clearProperty("auth.jwtSecret");
        }
    }
}
