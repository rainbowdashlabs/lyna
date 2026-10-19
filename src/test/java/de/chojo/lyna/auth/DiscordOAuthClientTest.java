/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import com.sun.net.httpserver.HttpServer;
import de.chojo.lyna.configuration.elements.discord.OAuth;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What the client makes of Discord's token and user endpoints, against a stand-in for them.
 */
class DiscordOAuthClientTest {
    private HttpServer server;
    private final AtomicReference<String> lastBody = new AtomicReference<>();
    private volatile int tokenStatus = 200;
    private volatile String tokenAnswer = "{}";

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/api/oauth2/token", exchange -> {
            lastBody.set(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            byte[] answer = tokenAnswer.getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(tokenStatus, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.createContext("/api/users/@me", exchange -> {
            boolean authorised =
                    "Bearer the-access".equals(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] answer = (authorised
                            ? "{\"id\":\"4242\",\"username\":\"nora\",\"email\":\"n@example.invalid\",\"verified\":true}"
                            : "{\"message\":\"401: Unauthorized\"}")
                    .getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(authorised ? 200 : 401, answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private DiscordOAuthClient client() {
        OAuth config = Mockito.mock(OAuth.class);
        Mockito.when(config.clientId()).thenReturn("client");
        Mockito.when(config.clientSecret()).thenReturn("secret");
        Mockito.when(config.redirectUri()).thenReturn("https://lyna.example.invalid/api/auth/discord/callback");
        Mockito.when(config.apiUrl())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort() + "/api/");
        return new DiscordOAuthClient(config);
    }

    @Test
    @DisplayName("A code is traded for the whole grant, refresh token and all")
    void exchangeReturnsTheGrant() throws Exception {
        tokenAnswer =
                "{\"access_token\":\"the-access\",\"refresh_token\":\"the-refresh\",\"scope\":\"identify email\",\"expires_in\":3600}";

        DiscordOAuthClient.Grant grant = client().exchangeCode("a-code");

        assertEquals("the-access", grant.accessToken());
        assertEquals("the-refresh", grant.refreshToken());
        assertEquals("identify email", grant.scope());
        assertEquals(Instant.EPOCH.plusSeconds(3600), grant.expiresAt(Instant.EPOCH));
        assertTrue(lastBody.get().startsWith("grant_type=authorization_code&code=a-code"));
        assertTrue(lastBody.get().contains("client_secret=secret"));
    }

    @Test
    @DisplayName("A refresh sends the refresh token and reads the rotated pair; a missing lifetime is a week")
    void refreshRotates() throws Exception {
        tokenAnswer = "{\"access_token\":\"new-access\",\"refresh_token\":\"new-refresh\"}";

        DiscordOAuthClient.Grant grant = client().refresh("old-refresh");

        assertTrue(lastBody.get().startsWith("grant_type=refresh_token&refresh_token=old-refresh"));
        assertEquals("new-refresh", grant.refreshToken());
        assertEquals("", grant.scope());
        assertEquals(604_800L, grant.expiresIn());
    }

    @Test
    @DisplayName("invalid_grant is told apart from every other failure")
    void invalidGrantIsItsOwnFailure() {
        tokenStatus = 400;
        tokenAnswer = "{\"error\":\"invalid_grant\"}";
        assertThrows(DiscordOAuthClient.InvalidGrantException.class, () -> client().refresh("revoked"));

        tokenAnswer = "{\"error\":\"invalid_client\"}";
        Exception other = assertThrows(DiscordOAuthClient.OAuthException.class, () -> client().refresh("x"));
        assertTrue(!(other instanceof DiscordOAuthClient.InvalidGrantException));

        tokenStatus = 502;
        tokenAnswer = "<html>bad gateway</html>";
        assertInstanceOf(
                DiscordOAuthClient.OAuthException.class, assertThrows(Exception.class, () -> client().refresh("x")));
    }

    @Test
    @DisplayName("An answer without an access token is a failure, one without a refresh token is not")
    void missingTokens() throws Exception {
        tokenAnswer = "{\"refresh_token\":\"r\"}";
        assertThrows(DiscordOAuthClient.OAuthException.class, () -> client().exchangeCode("c"));

        tokenAnswer = "{\"access_token\":\"a\"}";
        assertNull(client().exchangeCode("c").refreshToken());
    }

    @Test
    @DisplayName("The user is read with the access token, and a refused one is a failure")
    void fetchUser() throws Exception {
        DiscordOAuthClient.DiscordUser user = client().fetchUser("the-access");

        assertEquals(4242L, user.id());
        assertEquals("nora", user.handle());
        assertEquals("n@example.invalid", user.provedEmail().orElseThrow());
        assertThrows(DiscordOAuthClient.OAuthException.class, () -> client().fetchUser("wrong"));
    }

    @Test
    @DisplayName("Signing in asks Discord not to show its consent screen again")
    void authorizeSkipsConsent() {
        String url = client().buildAuthorizeUrl("state-1");

        assertTrue(url.startsWith("https://discord.com/oauth2/authorize?"));
        assertTrue(url.contains("&prompt=none"));
        assertTrue(url.contains("&state=state-1"));
    }
}
