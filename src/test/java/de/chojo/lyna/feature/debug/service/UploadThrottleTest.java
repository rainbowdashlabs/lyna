/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.service;

import de.chojo.lyna.configuration.elements.DebugReports;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * One debug report per address per interval.
 */
class UploadThrottleTest {

    @Test
    @DisplayName("An address uploads once per interval; another address is not held back by it")
    void oncePerAddress() {
        UploadThrottle throttle = new UploadThrottle(new DebugReports());

        assertTrue(throttle.admit("10.0.0.1"));
        assertFalse(throttle.admit("10.0.0.1"));
        assertTrue(throttle.admit("10.0.0.2"));
    }
}
