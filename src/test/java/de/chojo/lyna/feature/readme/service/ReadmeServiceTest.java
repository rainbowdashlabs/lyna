/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.readme.service;

import com.sun.net.httpserver.HttpServer;
import de.chojo.lyna.configuration.elements.Github;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Fetching a README from GitHub, against a stand-in that answers the way GitHub does.
 */
class ReadmeServiceTest {
    private static final String URL = "https://github.com/owner/repo";

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicInteger calls = new AtomicInteger();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> body = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        body.set(contents("# Hello\n\n![x](x.png)", "base64"));
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/owner/repo/readme", exchange -> {
            calls.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            byte[] answer = body.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), answer.length);
            exchange.getResponseBody().write(answer);
            exchange.close();
        });
        server.start();
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String contents(String markdown, String encoding) {
        return "{\"path\":\"README.md\",\"encoding\":\"%s\",\"html_url\":\"https://github.com/owner/repo/blob/main/README.md\",\"content\":\"%s\"}"
                .formatted(
                        encoding,
                        Base64.getMimeEncoder()
                                .encodeToString(markdown.getBytes(StandardCharsets.UTF_8))
                                .replace("\r\n", "\\n"));
    }

    private ReadmeService service(String token) {
        Github github = mock(Github.class);
        when(github.apiUrl())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(github.token()).thenReturn(token);
        return new ReadmeService(github);
    }

    @Test
    @DisplayName("A README is fetched, decoded and its links made absolute, and asked for once an hour")
    void fetchedAndCached() {
        ReadmeService readmes = service("");

        var readme = readmes.readme(URL).orElseThrow();
        readmes.readme(URL);

        assertTrue(readme.markdown().startsWith("# Hello"));
        assertTrue(readme.markdown().contains("https://raw.githubusercontent.com/owner/repo/HEAD/x.png"));
        assertEquals("https://github.com/owner/repo/blob/main/README.md", readme.url());
        assertEquals(1, calls.get());
        assertEquals(null, authorization.get());
    }

    @Test
    @DisplayName("A configured token is sent")
    void token() {
        service("secret").readme(URL);

        assertEquals("Bearer secret", authorization.get());
    }

    @Test
    @DisplayName("A project that is not a GitHub repository, or has no README, has none")
    void none() {
        status.set(404);

        assertTrue(service("").readme("https://example.com/x").isEmpty());
        assertTrue(service("").readme(URL).isEmpty());
    }

    @Test
    @DisplayName("Content that is not base64 or does not parse is no README")
    void unreadable() {
        body.set(contents("x", "utf-8"));
        assertTrue(service("").readme(URL).isEmpty());
        body.set("not json");
        assertTrue(service("").readme(URL).isEmpty());
    }

    @Test
    @DisplayName("Unreachable GitHub answers nothing rather than failing")
    void unreachable() {
        Github github = mock(Github.class);
        when(github.apiUrl()).thenReturn("http://127.0.0.1:1");
        when(github.token()).thenReturn("");

        assertTrue(new ReadmeService(github).readme(URL).isEmpty());
    }
}
