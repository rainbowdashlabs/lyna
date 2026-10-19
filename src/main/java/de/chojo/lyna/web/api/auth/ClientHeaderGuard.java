/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import io.javalin.http.Context;
import io.javalin.http.ForbiddenResponse;
import io.javalin.http.HandlerType;

import java.util.Set;

/**
 * Refuses a request that changes something on the strength of the session cookie alone.
 *
 * <p>A browser attaches the cookie by itself, which a header never was. {@code SameSite=Lax} keeps it
 * off cross-site posts already; this is the second lock. The application's own client names itself
 * in {@value #HEADER} on every request, and a page on another site cannot add a custom header to a
 * form post, nor to a {@code fetch} without a CORS preflight this backend never grants.
 *
 * <p>Requests without the cookie are left alone: the Ko-fi webhook, plugin update checks and anything
 * else that does not sign in has nothing here to forge.
 */
public final class ClientHeaderGuard {
    public static final String HEADER = "X-Lyna-Client";
    public static final String WEB = "web";
    private static final Set<HandlerType> SAFE = Set.of(HandlerType.GET, HandlerType.HEAD, HandlerType.OPTIONS);

    private ClientHeaderGuard() {}

    /**
     * @throws ForbiddenResponse when the request carries the session cookie, changes something and
     *     does not name the application's client
     */
    public static void check(Context ctx) {
        if (!allowed(ctx.method(), SessionCookie.read(ctx) != null, ctx.header(HEADER))) {
            throw new ForbiddenResponse("Requests made with a session must come from the Lyna web client");
        }
    }

    static boolean allowed(HandlerType method, boolean carriesSession, String client) {
        if (SAFE.contains(method) || !carriesSession) return true;
        return WEB.equals(client);
    }
}
