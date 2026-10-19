/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Which requests the session cookie alone is not enough for.
 */
class ClientHeaderGuardTest {

    @Test
    @DisplayName("A change made with the session cookie needs the client header")
    void unsafeWithCookieNeedsTheHeader() {
        for (HandlerType method :
                new HandlerType[] {HandlerType.POST, HandlerType.PUT, HandlerType.PATCH, HandlerType.DELETE}) {
            assertFalse(ClientHeaderGuard.allowed(method, true, null), method + " without the header");
            assertFalse(ClientHeaderGuard.allowed(method, true, "other"), method + " naming another client");
            assertTrue(ClientHeaderGuard.allowed(method, true, "web"), method + " from the web client");
        }
    }

    @Test
    @DisplayName("Reading needs no header, and neither does anything sent without a session")
    void safeOrSessionlessPasses() {
        assertTrue(ClientHeaderGuard.allowed(HandlerType.GET, true, null));
        assertTrue(ClientHeaderGuard.allowed(HandlerType.HEAD, true, null));
        assertTrue(ClientHeaderGuard.allowed(HandlerType.OPTIONS, true, null));
        assertTrue(ClientHeaderGuard.allowed(HandlerType.POST, false, null), "the Ko-fi webhook has no session");
    }

    @Test
    @DisplayName("A forged change is refused as forbidden before any handler sees it")
    void checkRefusesForgedRequests() {
        Context forged = mock(Context.class);
        when(forged.method()).thenReturn(HandlerType.POST);
        when(forged.cookie(SessionCookie.NAME)).thenReturn("a.b.c");
        assertThrows(ForbiddenResponse.class, () -> ClientHeaderGuard.check(forged));

        Context genuine = mock(Context.class);
        when(genuine.method()).thenReturn(HandlerType.POST);
        when(genuine.cookie(SessionCookie.NAME)).thenReturn("a.b.c");
        when(genuine.header(ClientHeaderGuard.HEADER)).thenReturn("web");
        assertDoesNotThrow(() -> ClientHeaderGuard.check(genuine));
    }
}
