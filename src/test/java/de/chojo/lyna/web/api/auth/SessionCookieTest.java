/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import de.chojo.lyna.auth.JwtService;
import io.javalin.http.Context;
import io.javalin.http.Cookie;
import io.javalin.http.SameSite;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.Duration;
import java.time.Instant;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * The attributes the session cookie is handed out with, which are what keep it out of scripts and
 * off cross-site requests.
 */
class SessionCookieTest {

    private static Context request(String scheme, String forwardedProto) {
        Context ctx = mock(Context.class);
        when(ctx.scheme()).thenReturn(scheme);
        when(ctx.header("X-Forwarded-Proto")).thenReturn(forwardedProto);
        return ctx;
    }

    private static Cookie written(Context ctx) {
        ArgumentCaptor<Cookie> cookie = ArgumentCaptor.forClass(Cookie.class);
        verify(ctx).cookie(cookie.capture());
        return cookie.getValue();
    }

    @Test
    @DisplayName("The session goes out HttpOnly, SameSite=Lax, scoped to the API, for as long as the token lasts")
    void writeSetsTheAttributes() {
        Context ctx = request("http", null);
        Instant expires = Instant.now().plus(Duration.ofDays(30));

        SessionCookie.write(ctx, new JwtService.Issued("a.b.c", "jti", expires));

        Cookie cookie = written(ctx);
        assertEquals(SessionCookie.NAME, cookie.getName());
        assertEquals("a.b.c", cookie.getValue());
        assertEquals("/api", cookie.getPath());
        assertTrue(cookie.isHttpOnly());
        assertEquals(SameSite.LAX, cookie.getSameSite());
        assertFalse(cookie.getSecure(), "plain HTTP never sees a secure cookie again");
        assertTrue(Math.abs(cookie.getMaxAge() - Duration.ofDays(30).toSeconds()) < 5);
    }

    @Test
    @DisplayName("A token that has already run out is written as gone")
    void expiredTokenHasNoAge() {
        Context ctx = request("http", null);

        SessionCookie.write(ctx, new JwtService.Issued("t", "jti", Instant.now().minusSeconds(60)));

        assertEquals(0, written(ctx).getMaxAge());
    }

    @Test
    @DisplayName("Clearing sends the same cookie empty and already expired")
    void clearExpiresTheCookie() {
        Context ctx = request("http", null);

        SessionCookie.clear(ctx);

        Cookie cookie = written(ctx);
        assertEquals(SessionCookie.NAME, cookie.getName());
        assertEquals("", cookie.getValue());
        assertEquals(0, cookie.getMaxAge());
        assertEquals("/api", cookie.getPath());
    }

    @Test
    @DisplayName("Secure over HTTPS, whether seen here or reported by the proxy in front")
    void secureFollowsTheScheme() {
        assertTrue(SessionCookie.secure(request("https", null)));
        assertTrue(SessionCookie.secure(request("http", "https")));
        assertTrue(SessionCookie.secure(request("http", "HTTPS, http")));
        assertFalse(SessionCookie.secure(request("http", "http")));
        assertFalse(SessionCookie.secure(request("http", null)));
    }

    @Test
    @DisplayName("The token is read from the cookie of that name")
    void readTakesTheCookie() {
        Context ctx = mock(Context.class);
        when(ctx.cookie(SessionCookie.NAME)).thenReturn("x.y.z");

        assertEquals("x.y.z", SessionCookie.read(ctx));
    }
}
