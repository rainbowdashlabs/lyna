/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.auth;

import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.Optional;

/**
 * What a Discord sign-in carries from its start to its callback, in a cookie of its own: the state
 * Discord has to hand back, and the page to land on afterwards.
 *
 * <p>The page is only ever a path on this site. Anything else - another host, a protocol-relative
 * {@code //host}, a backslash some browsers read as a slash - would let a crafted link send somebody
 * elsewhere with a fresh session behind them, and is replaced by {@link #DEFAULT_NEXT}.
 */
public record OAuthState(String state, String next) {
    public static final String DEFAULT_NEXT = "/account";
    private static final char SEPARATOR = '.';

    public OAuthState {
        next = localPath(next);
    }

    /**
     * @return the cookie value: the state, then the page encoded so that nothing in it breaks a cookie
     */
    public String encode() {
        return state
                + SEPARATOR
                + Base64.getUrlEncoder().withoutPadding().encodeToString(next.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * @return what the cookie carried, or empty when it carries nothing readable
     */
    public static Optional<OAuthState> decode(String cookie) {
        if (cookie == null || cookie.isBlank()) return Optional.empty();
        int split = cookie.indexOf(SEPARATOR);
        if (split < 0) return Optional.of(new OAuthState(cookie, DEFAULT_NEXT));
        try {
            String next =
                    new String(Base64.getUrlDecoder().decode(cookie.substring(split + 1)), StandardCharsets.UTF_8);
            return Optional.of(new OAuthState(cookie.substring(0, split), next));
        } catch (IllegalArgumentException e) {
            return Optional.of(new OAuthState(cookie.substring(0, split), DEFAULT_NEXT));
        }
    }

    /**
     * @return the path when it is one on this site, otherwise {@link #DEFAULT_NEXT}
     */
    static String localPath(String raw) {
        if (raw == null || !raw.startsWith("/") || raw.startsWith("//") || raw.contains("\\")) return DEFAULT_NEXT;
        for (char c : raw.toCharArray()) {
            if (Character.isISOControl(c) || Character.isWhitespace(c)) return DEFAULT_NEXT;
        }
        return raw;
    }
}
