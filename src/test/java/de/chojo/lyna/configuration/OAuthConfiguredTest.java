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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Whether Discord sign-in is offered at all. An instance that has not been given a Discord application
 * refuses sign-in instead of sending somebody to a Discord error page.
 */
class OAuthConfiguredTest {

    @Test
    @DisplayName("A fresh instance has no Discord application, so sign-in is not offered")
    void freshInstanceIsNotConfigured(@TempDir Path directory) {
        assertFalse(new Conf(directory).main().discord().oauth().configured());
    }

    @Test
    @DisplayName("A missing secret is as good as none")
    void missingSecretIsNotConfigured(@TempDir Path directory) throws Exception {
        write(directory, "1", "", "https://example.invalid/callback");

        assertFalse(new Conf(directory).main().discord().oauth().configured());
    }

    @Test
    @DisplayName("An id, a secret and a return address are what it takes")
    void allThreeAreConfigured(@TempDir Path directory) throws Exception {
        write(directory, "1", "secret", "https://example.invalid/callback");

        assertTrue(new Conf(directory).main().discord().oauth().configured());
    }

    private static void write(Path directory, String id, String secret, String redirect) throws Exception {
        Files.writeString(directory.resolve("config.yaml"), """
                discord:
                  oauth:
                    clientId: "%s"
                    clientSecret: "%s"
                    redirectUri: "%s"
                """.formatted(id, secret, redirect));
    }
}
