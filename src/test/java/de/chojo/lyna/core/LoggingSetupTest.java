/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.core;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A logging setup is written once where Log4j looks, and never over one that is there.
 */
class LoggingSetupTest {
    @Test
    @DisplayName("A missing file is written, with its directory")
    void missingFileIsWritten(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("config").resolve("log4j2.xml");

        assertTrue(LoggingSetup.writeDefault(file));

        assertTrue(Files.readString(file).contains("<Configuration"));
    }

    @Test
    @DisplayName("A file that is there is left as it is")
    void existingFileIsKept(@TempDir Path directory) throws IOException {
        Path file = directory.resolve("log4j2.xml");
        Files.writeString(file, "mine");

        assertFalse(LoggingSetup.writeDefault(file));

        assertEquals("mine", Files.readString(file));
    }
}
