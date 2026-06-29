package de.chojo.lyna.auth;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.configuration.elements.Discord;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;

public class DiscordOAuthClient {
    private static final String AUTHORIZE_URL = "https://discord.com/oauth2/authorize";
    private static final String TOKEN_URL = "https://discord.com/api/oauth2/token";
    private static final String USER_URL = "https://discord.com/api/users/@me";
    private static final String SCOPE = "identify";

    private final Discord.OAuth config;
    private final HttpClient http;
    private final ObjectMapper json = new ObjectMapper();

    public DiscordOAuthClient(Discord.OAuth config) {
        this.config = config;
        this.http = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    public String buildAuthorizeUrl(String state) {
        return AUTHORIZE_URL
                + "?response_type=code"
                + "&client_id=" + url(config.clientId())
                + "&scope=" + url(SCOPE)
                + "&redirect_uri=" + url(config.redirectUri())
                + "&state=" + url(state)
                + "&prompt=consent";
    }

    public String exchangeCode(String code) throws Exception {
        String body = "grant_type=authorization_code"
                + "&code=" + url(code)
                + "&redirect_uri=" + url(config.redirectUri())
                + "&client_id=" + url(config.clientId())
                + "&client_secret=" + url(config.clientSecret());

        HttpRequest request = HttpRequest.newBuilder(URI.create(TOKEN_URL))
                .header("Content-Type", "application/x-www-form-urlencoded")
                .header("Accept", "application/json")
                .POST(HttpRequest.BodyPublishers.ofString(body))
                .build();

        HttpResponse<String> response = http.send(request, HttpResponse.BodyHandlers.ofString());
        if (response.statusCode() / 100 != 2) {
            throw new OAuthException("Token exchange failed: HTTP " + response.statusCode() + " " + response.body());
        }
        JsonNode node = json.readTree(response.body());
        JsonNode accessToken = node.get("access_token");
        if (accessToken == null || accessToken.isNull()) {
            throw new OAuthException("Token response missing access_token: " + response.body());
        }
        return accessToken.asText();
    }

    public DiscordUser fetchUser(String accessToken) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create(USER_URL))
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
                node.path("global_name").asText(null));
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record DiscordUser(long id, String username, String globalName) {
    }

    public static class OAuthException extends Exception {
        public OAuthException(String message) {
            super(message);
        }
    }
}
