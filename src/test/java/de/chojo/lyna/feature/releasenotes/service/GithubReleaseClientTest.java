/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasenotes.service;

import com.sun.net.httpserver.HttpServer;
import de.chojo.lyna.configuration.elements.Github;
import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.entity.GithubRepository;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Asking GitHub for releases, against a stand-in that answers the way GitHub does.
 */
class GithubReleaseClientTest {
    private static final GithubRepository REPOSITORY = new GithubRepository("owner", "repo");
    private static final String RELEASES = """
            [
              {"id": 3, "tag_name": "v3", "name": "Third", "body": "**bold**", "html_url": "https://github.com/owner/repo/releases/tag/v3",
               "prerelease": true, "draft": false, "published_at": "2026-10-03T10:00:00Z"},
              {"id": 2, "tag_name": "v2", "name": " ", "body": "", "html_url": "https://github.com/owner/repo/releases/tag/v2",
               "prerelease": false, "draft": false, "published_at": "2026-10-02T10:00:00Z"},
              {"id": 9, "tag_name": "v9", "name": "Draft", "body": "x", "html_url": "https://github.com/owner/repo/releases/tag/v9",
               "prerelease": false, "draft": true, "published_at": null},
              {"id": 8, "tag_name": "v8", "html_url": "https://github.com/owner/repo/releases/tag/v8",
               "prerelease": false, "draft": false, "published_at": null},
              {"id": 7, "tag_name": "v7", "html_url": "https://github.com/owner/repo/releases/tag/v7",
               "prerelease": false, "draft": false, "published_at": "yesterday"}
            ]
            """;

    private HttpServer server;
    private final AtomicInteger status = new AtomicInteger(200);
    private final AtomicReference<String> body = new AtomicReference<>(RELEASES);
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> ifNoneMatch = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/repos/owner/repo/releases", exchange -> {
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            ifNoneMatch.set(exchange.getRequestHeaders().getFirst("If-None-Match"));
            query.set(exchange.getRequestURI().getQuery());
            exchange.getResponseHeaders().add("ETag", "\"abc\"");
            if (status.get() == 304) {
                exchange.sendResponseHeaders(304, -1);
                exchange.close();
                return;
            }
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

    private GithubReleaseClient client(String token) {
        Github github = mock(Github.class);
        when(github.apiUrl())
                .thenReturn("http://127.0.0.1:" + server.getAddress().getPort());
        when(github.token()).thenReturn(token);
        return new GithubReleaseClient(github);
    }

    @Test
    @DisplayName("Published releases come back with the ETag; drafts and undated releases are left out")
    void releases() {
        var answer = assertInstanceOf(
                GithubReleaseClient.Answer.Releases.class, client("").releases(REPOSITORY, null));

        assertEquals("\"abc\"", answer.etag());
        assertEquals(
                List.of(
                        new GithubRelease(
                                3,
                                "v3",
                                "Third",
                                "**bold**",
                                "https://github.com/owner/repo/releases/tag/v3",
                                true,
                                Instant.parse("2026-10-03T10:00:00Z")),
                        new GithubRelease(
                                2,
                                "v2",
                                null,
                                null,
                                "https://github.com/owner/repo/releases/tag/v2",
                                false,
                                Instant.parse("2026-10-02T10:00:00Z"))),
                answer.releases());
        assertEquals("per_page=100", query.get());
        assertNull(authorization.get());
        assertNull(ifNoneMatch.get());
    }

    @Test
    @DisplayName("The last ETag and the token are sent, and a 304 says nothing changed")
    void unchanged() {
        status.set(304);

        var answer = client("secret").releases(REPOSITORY, "\"abc\"");

        assertInstanceOf(GithubReleaseClient.Answer.Unchanged.class, answer);
        assertEquals("\"abc\"", ifNoneMatch.get());
        assertEquals("Bearer secret", authorization.get());
    }

    @Test
    @DisplayName("A refusal, an unreadable answer and an unreachable GitHub all fail")
    void failures() {
        status.set(403);
        assertInstanceOf(GithubReleaseClient.Answer.Failed.class, client("").releases(REPOSITORY, null));

        status.set(200);
        body.set("not json");
        assertInstanceOf(GithubReleaseClient.Answer.Failed.class, client("").releases(REPOSITORY, null));

        GithubReleaseClient unreachable = client("");
        server.stop(0);
        assertInstanceOf(GithubReleaseClient.Answer.Failed.class, unreachable.releases(REPOSITORY, null));
    }
}
