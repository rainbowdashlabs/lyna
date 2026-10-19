/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.configuration.elements.discord.OAuth;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

public class DiscordOAuthClient {
    private static final String AUTHORIZE_URL = "https://discord.com/oauth2/authorize";
    /** What Discord grants an access token for when its answer does not say: a week. */
    private static final long DEFAULT_EXPIRY_SECONDS = 604_800L;
    /**
     * {@code email} as well as {@code identify}, so that somebody signing in with Discord arrives
     * with an address already attached. Discord says whether it has verified that address, and only
     * an address it has verified is treated as proved here.
     */
    private static final String SCOPE = "identify email";

    private final OAuth config;
    private final String tokenUrl;
    private final String userUrl;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public DiscordOAuthClient(OAuth config) {
        this.config = config;
        String api = config.apiUrl().replaceAll("/+$", "");
        this.tokenUrl = api + "/oauth2/token";
        this.userUrl = api + "/users/@me";
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * The address that asks Discord to vouch for somebody.
     *
     * <p>{@code prompt=none}: Discord skips its consent screen for somebody who has granted these
     * scopes before, so signing in again is a redirect there and back. Somebody who has not is still
     * asked.
     */
    public String buildAuthorizeUrl(String state) {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + url(config.clientId())
                + "&scope=" + url(SCOPE)
                + "&redirect_uri=" + url(config.redirectUri())
                + "&state=" + url(state)
                + "&prompt=none";
    }

    /**
     * Trades the code Discord handed back for a grant.
     */
    public Grant exchangeCode(String code) throws Exception {
        return requestToken(
                "grant_type=authorization_code" + "&code=" + url(code) + "&redirect_uri=" + url(config.redirectUri()));
    }

    /**
     * Trades a refresh token for a new grant. Discord rotates the refresh token as it does so: the one
     * passed in is spent, and only the one in the answer works from now on.
     *
     * @throws InvalidGrantException when Discord no longer honours the refresh token, which is what
     *     somebody removing the application from their Discord account looks like from here
     */
    public Grant refresh(String refreshToken) throws Exception {
        return requestToken("grant_type=refresh_token&refresh_token=" + url(refreshToken));
    }

    private Grant requestToken(String grant) throws Exception {
        String body = grant + "&client_id=" + url(config.clientId()) + "&client_secret=" + url(config.clientSecret());

        HttpRequest request = HttpRequest.newBuilder(URI.create(tokenUrl))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            if ("invalid_grant".equals(errorOf(response.body()))) {
                throw new InvalidGrantException("Discord refused the grant: " + response.body());
            }
            throw new OAuthException("Token request failed: HTTP " + response.statusCode() + " " + response.body());
        }
        JsonNode node = json.readTree(response.body());
        String accessToken = node.path("access_token").asText(null);
        if (accessToken == null) {
            throw new OAuthException("Token response missing access_token: " + response.body());
        }
        return new Grant(
                accessToken,
                node.path("refresh_token").asText(null),
                node.path("scope").asText(""),
                node.path("expires_in").asLong(DEFAULT_EXPIRY_SECONDS));
    }

    private String errorOf(String body) {
        try {
            return json.readTree(body).path("error").asText(null);
        } catch (Exception e) {
            return null;
        }
    }

    public DiscordUser fetchUser(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(userUrl))
                .header("Authorization", "Bearer " + accessToken)
                .header("Accept", "application/json")
                .GET()
                .build();
        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new OAuthException("User fetch failed: HTTP " + response.statusCode() + " " + response.body());
        }
        JsonNode node = json.readTree(response.body());
        String id = node.path("id").asText(null);
        if (id == null) {
            throw new OAuthException("User response missing id: " + response.body());
        }
        return new DiscordUser(
                Long.parseLong(id),
                node.path("username").asText(null),
                node.path("global_name").asText(null),
                node.path("email").asText(null),
                node.path("verified").asBoolean(false));
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    /**
     * @param username      the unique handle, which is what an account is named after
     * @param globalName    the display name, used only when an account predates unique handles and
     *                      has none
     * @param email         the address on the Discord account, when the scope was granted
     * @param emailVerified whether Discord has verified that address. Only then is it worth
     *                      anything: an unverified one is a string somebody typed into Discord, and
     *                      treating it as proved here would hand them whatever was bought with it
     */
    public record DiscordUser(long id, String username, String globalName, String email, boolean emailVerified) {
        /**
         * @return the address, but only when Discord has verified it
         */
        public java.util.Optional<String> provedEmail() {
            if (!emailVerified || email == null || email.isBlank()) return java.util.Optional.empty();
            return java.util.Optional.of(email.trim());
        }

        /**
         * @return what to call this person, preferring the handle they are addressed by
         */
        public String handle() {
            if (username != null && !username.isBlank()) return username;
            return globalName == null || globalName.isBlank() ? null : globalName;
        }
    }

    /**
     * What Discord granted.
     *
     * @param refreshToken null when Discord sent none, which leaves nothing to refresh with
     * @param expiresIn    how long the access token lasts, in seconds
     */
    public record Grant(String accessToken, String refreshToken, String scope, long expiresIn) {
        public Instant expiresAt(Instant now) {
            return now.plusSeconds(expiresIn);
        }
    }

    public static class OAuthException extends Exception {
        public OAuthException(String message) {
            super(message);
        }
    }

    /**
     * Discord no longer honours a grant.
     */
    public static class InvalidGrantException extends OAuthException {
        public InvalidGrantException(String message) {
            super(message);
        }
    }
}
