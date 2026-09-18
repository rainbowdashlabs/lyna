/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.util;

import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

class VersionTest {

    @Test
    void test() {
        Assertions.assertTrue(Version.parse("2.5.0-DEV").isOlder(Version.parse("2.5.0")));
        Assertions.assertTrue(Version.parse("2.5.0-SNAPSHOT").isOlder(Version.parse("2.5.0-DEV")));
        Assertions.assertTrue(Version.parse("2.5.0").isNewer(Version.parse("2.5.0-SNAPSHOT")));
    }
}
