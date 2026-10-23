/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web;

import de.chojo.lyna.configuration.TestConf;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * The server does not start without the address it is reached at.
 */
class WebServiceAddressTest {
    @Test
    @DisplayName("Without a public address, start-up stops and names both settings")
    void missingAddressIsNamed() {
        var api = TestConf.defaults().main().api();

        var refused = assertThrows(IllegalStateException.class, () -> WebService.requireAddress(api));

        assertTrue(refused.getMessage().contains("api.hostname"));
        assertTrue(refused.getMessage().contains("api.url"));
    }

    @Test
    @DisplayName("With both set, it starts")
    void addressSetStarts() throws Exception {
        var api = TestConf.from("""
                        api:
                          hostname: "lyna.example.invalid"
                          url: "https://lyna.example.invalid"
                        """).main().api();

        assertDoesNotThrow(() -> WebService.requireAddress(api));
    }
}
