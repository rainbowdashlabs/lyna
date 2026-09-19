/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.Arrays;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * The address a finished Discord sign-in sends the browser to.
 */
class DiscordLandingTest {

    @Test
    @DisplayName("The session rides in the fragment, which no server is ever sent")
    void sessionIsInTheFragment() {
        URI landing = URI.create(Auth.landing("a.b.c", "/account"));

        assertEquals("/auth/discord", landing.getPath());
        assertNull(landing.getRawQuery());
        assertEquals("a.b.c", fragment(landing).get("token"));
    }

    @Test
    @DisplayName("Where to go next survives the trip, query and all")
    void nextSurvives() {
        URI landing = URI.create(Auth.landing("t", "/account/security?linked=1&x=y"));

        assertEquals("/account/security?linked=1&x=y", fragment(landing).get("next"));
    }

    private static Map<String, String> fragment(URI uri) {
        return Arrays.stream(uri.getRawFragment().split("&"))
                .map(pair -> pair.split("=", 2))
                .collect(Collectors.toMap(pair -> pair[0], pair -> URLDecoder.decode(pair[1], StandardCharsets.UTF_8)));
    }
}
