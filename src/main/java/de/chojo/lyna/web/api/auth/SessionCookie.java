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

import java.time.Duration;
import java.time.Instant;

/**
 * The cookie a browser's session travels in.
 *
 * <p>{@code HttpOnly}, so no script on the page can read it. {@code SameSite=Lax}, so it still
 * arrives on the top-level redirect back from Discord - which is how linking Discord finds the
 * account it is linking to - while a cross-site form post goes without it. {@code Secure} whenever the
 * request reached us over HTTPS, directly or through a proxy that says so; the development and
 * end-to-end stacks run on plain HTTP and would never see a secure cookie again.
 *
 * <p>Scoped to {@code /api}, the only place that reads it.
 */
public final class SessionCookie {
    public static final String NAME = "lyna_session";
    static final String PATH = "/api";

    private SessionCookie() {}

    /**
     * @return the token the browser sent, or null when it sent none
     */
    public static String read(Context ctx) {
        return ctx.cookie(NAME);
    }

    /**
     * Hands the browser a session's token, to keep until the token expires.
     */
    public static void write(Context ctx, JwtService.Issued issued) {
        long seconds =
                Math.max(0, Duration.between(Instant.now(), issued.expiresAt()).toSeconds());
        ctx.cookie(cookie(ctx, issued.token(), (int) Math.min(seconds, Integer.MAX_VALUE)));
    }

    /**
     * Tells the browser to forget its session.
     */
    public static void clear(Context ctx) {
        ctx.cookie(cookie(ctx, "", 0));
    }

    /**
     * @return whether the browser reached us over HTTPS, as seen here or as the proxy in front says
     */
    static boolean secure(Context ctx) {
        if ("https".equalsIgnoreCase(ctx.scheme())) return true;
        String forwarded = ctx.header("X-Forwarded-Proto");
        if (forwarded == null) return false;
        return "https".equalsIgnoreCase(forwarded.split(",", 2)[0].trim());
    }

    private static Cookie cookie(Context ctx, String value, int maxAge) {
        return new Cookie(NAME, value, PATH, maxAge, secure(ctx), 0, true, null, null, SameSite.LAX);
    }
}
