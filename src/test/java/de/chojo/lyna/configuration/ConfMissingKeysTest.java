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

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What happens to a configuration file written by an older version.
 *
 * <p>Ocular writes the file when it is missing and never again, so every setting added since an
 * instance was set up is absent from its file. That is how a deployment arrives with no sign-in
 * secret and no Discord credentials while nothing says so.
 */
class ConfMissingKeysTest {

    @Test
    @DisplayName("A file from an older version gains the settings this one understands")
    void missingSectionsAreAdded(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), """
                baseSettings:
                  token: "a-token"
                database:
                  host: "db.example.invalid"
                """);

        new Conf(directory);

        String written = Files.readString(directory.resolve("config.yaml"));
        assertTrue(written.contains("auth:"), "the sign-in settings are there now");
        assertTrue(written.contains("jwtSecret"), "including the one that stops the web layer starting");
        assertTrue(written.contains("discord:"), "and the Discord ones");
        assertTrue(written.contains("clientSecret"), "including the OAuth secret");
    }

    @Test
    @DisplayName("What was already written is left exactly as it was")
    void existingValuesSurvive(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), """
                baseSettings:
                  token: "a-token"
                database:
                  host: "db.example.invalid"
                  password: "kept"
                """);

        new Conf(directory);

        Conf reread = new Conf(directory);
        assertEquals("a-token", reread.main().baseSettings().token());
        assertEquals("db.example.invalid", reread.main().database().host());
        assertEquals("kept", reread.main().database().password());
    }

    @Test
    @DisplayName("A section that exists but lacks a setting gains only that setting")
    void partialSectionsAreFilled(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), """
                database:
                  host: "db.example.invalid"
                  schema: "mine"
                """);

        new Conf(directory);

        Conf reread = new Conf(directory);
        assertEquals("db.example.invalid", reread.main().database().host());
        assertEquals("mine", reread.main().database().schema());
        assertTrue(Files.readString(directory.resolve("config.yaml")).contains("poolSize"));
    }

    @Test
    @DisplayName("Nothing an override supplied is written to the file")
    void overridesAreNotWrittenBack(@TempDir Path directory) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), "database:\n  host: \"db.example.invalid\"\n");
        System.setProperty("db.password", "from-the-environment");
        try {
            new Conf(directory);

            assertTrue(
                    !Files.readString(directory.resolve("config.yaml")).contains("from-the-environment"),
                    "a password supplied from outside belongs outside the file");
        } finally {
            System.clearProperty("db.password");
        }
    }
}
