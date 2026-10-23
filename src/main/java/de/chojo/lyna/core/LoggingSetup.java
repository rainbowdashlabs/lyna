/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.core;

import org.apache.logging.log4j.core.config.Configurator;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Objects;

/**
 * Makes sure there is a logging setup to read before anything logs.
 *
 * <p>Neither the image nor a release brings one, and the start script points Log4j at
 * {@code config/log4j2.xml}. Without that file Log4j falls back to printing errors alone, so an
 * instance that fails to start would say nothing about why. The file is written once, the way
 * {@code config.yaml} is, and never touched again.
 */
public final class LoggingSetup {
    /** Where the start script tells Log4j to look, unless told otherwise. */
    static final String DEFAULT_FILE = "config/log4j2.xml";

    private static final String TEMPLATE = "/logging/log4j2.xml";

    private LoggingSetup() {}

    /**
     * Writes the default setup to the file Log4j is pointed at, if it does not exist yet, and has Log4j
     * read it.
     */
    public static void ensureConfiguration() {
        String configured = System.getProperty("log4j.configurationFile");
        Path file = Path.of(configured == null || configured.isBlank() ? DEFAULT_FILE : configured);
        if (writeDefault(file)) Configurator.reconfigure(file.toUri());
    }

    /**
     * @return whether the file was written, which is only when it did not exist
     */
    static boolean writeDefault(Path file) {
        if (Files.exists(file)) return false;
        try (InputStream template =
                Objects.requireNonNull(LoggingSetup.class.getResourceAsStream(TEMPLATE), TEMPLATE)) {
            Path parent = file.toAbsolutePath().getParent();
            if (parent != null) Files.createDirectories(parent);
            Files.copy(template, file);
            return true;
        } catch (IOException e) {
            throw new UncheckedIOException("Could not write the default logging setup to " + file, e);
        }
    }
}
