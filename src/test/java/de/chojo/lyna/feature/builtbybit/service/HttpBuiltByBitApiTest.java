/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.service;

import com.sun.net.httpserver.HttpServer;
import de.chojo.lyna.configuration.elements.BuiltByBit;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;
import de.chojo.lyna.feature.builtbybit.service.BuiltByBitApi.Verdict;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Asking BuiltByBit whether a member holds a licence, against a stand-in that answers the way its API
 * does.
 */
class HttpBuiltByBitApiTest {
    private static final long MEMBER = 42;
    private static final long NOW = 1_700_000_000L;
    private static final BuiltByBitListing RESOURCE = BuiltByBitListing.resource(1234);
    private static final BuiltByBitListing ADDON = new BuiltByBitListing(1234, 77);

    private HttpServer server;
    private final Deque<Answer> answers = new ArrayDeque<>();
    private final AtomicReference<String> authorization = new AtomicReference<>();
    private final AtomicReference<String> query = new AtomicReference<>();
    private final AtomicInteger requests = new AtomicInteger();
    private HttpBuiltByBitApi api;

    private record Answer(int status, String body, String retryAfter) {}

    @BeforeEach
    void start() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/v2/resources/creator/licenses", exchange -> {
            requests.incrementAndGet();
            authorization.set(exchange.getRequestHeaders().getFirst("Authorization"));
            query.set(exchange.getRequestURI().getQuery());
            Answer answer = answers.isEmpty() ? new Answer(500, "", null) : answers.poll();
            if (answer.retryAfter() != null) exchange.getResponseHeaders().add("Retry-After", answer.retryAfter());
            byte[] body = answer.body().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(answer.status(), body.length == 0 ? -1 : body.length);
            if (body.length > 0) exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();

        BuiltByBit config = mock(BuiltByBit.class);
        when(config.apiUrl())
                .thenReturn("http://127.0.0.1:%d/".formatted(server.getAddress().getPort()));
        when(config.apiToken()).thenReturn("secret-token");
        api = new HttpBuiltByBitApi(config, Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    @AfterEach
    void stop() {
        server.stop(0);
    }

    private static String licenses(String... licenses) {
        return "{\"result\":\"success\",\"data\":{\"licenses\":[" + String.join(",", licenses) + "]}}";
    }

    private static String permanent(long buyer, long content, boolean active) {
        return "{\"content_type\":\"resource\",\"content_id\":%d,\"buyer_id\":%d,\"permanent\":true,\"active\":%b}"
                .formatted(content, buyer, active);
    }

    private static String temporary(long content, long start, long end) {
        return "{\"content_type\":\"resource\",\"content_id\":%d,\"buyer_id\":%d,\"permanent\":false,\"start_date\":%d,\"end_date\":%d}"
                .formatted(content, MEMBER, start, end);
    }

    @Test
    @DisplayName("Asks for the member's licences of the resource with the token")
    void asksWithToken() {
        answers.add(new Answer(200, licenses(permanent(MEMBER, 1234, true)), null));

        assertEquals(Verdict.CONFIRMED, api.confirm(MEMBER, RESOURCE));
        assertEquals("Token secret-token", authorization.get());
        assertEquals("resource_ids=1234&buyer_ids=42", query.get());
    }

    @Test
    @DisplayName("An inactive permanent licence, another member's or another resource's, is no licence")
    void otherLicencesDeny() {
        answers.add(new Answer(
                200,
                licenses(permanent(MEMBER, 1234, false), permanent(7, 1234, true), permanent(MEMBER, 99, true)),
                null));

        assertEquals(Verdict.DENIED, api.confirm(MEMBER, RESOURCE));
    }

    @Test
    @DisplayName("A temporary licence counts only between its start and end")
    void temporaryLicenceCountsInItsWindow() {
        answers.add(new Answer(200, licenses(temporary(1234, NOW - 10, NOW + 10)), null));
        answers.add(new Answer(200, licenses(temporary(1234, NOW - 20, NOW - 10)), null));

        assertEquals(Verdict.CONFIRMED, api.confirm(MEMBER, RESOURCE));
        assertEquals(Verdict.DENIED, api.confirm(MEMBER, RESOURCE));
    }

    @Test
    @DisplayName("An add-on is matched by its add-on id")
    void addonMatchesAddonId() {
        answers.add(new Answer(200, licenses(permanent(MEMBER, 1234, true)), null));
        answers.add(new Answer(200, licenses(permanent(MEMBER, 77, true)), null));

        assertEquals(Verdict.DENIED, api.confirm(MEMBER, ADDON));
        assertEquals(Verdict.CONFIRMED, api.confirm(MEMBER, ADDON));
    }

    @Test
    @DisplayName("No licences at all is a denial")
    void emptyDenies() {
        answers.add(new Answer(200, licenses(), null));

        assertEquals(Verdict.DENIED, api.confirm(MEMBER, RESOURCE));
    }

    @Test
    @DisplayName("An error is no answer")
    void errorIsUnavailable() {
        answers.add(new Answer(500, "oops", null));

        assertEquals(Verdict.UNAVAILABLE, api.confirm(MEMBER, RESOURCE));
    }

    @Test
    @DisplayName("A body that is not JSON is no answer")
    void garbageIsUnavailable() {
        answers.add(new Answer(200, "<html>", null));

        assertEquals(Verdict.UNAVAILABLE, api.confirm(MEMBER, RESOURCE));
    }

    @Test
    @DisplayName("A short rate limit is waited out and asked again")
    void shortRateLimitIsRetried() {
        answers.add(new Answer(429, "", "10"));
        answers.add(new Answer(200, licenses(permanent(MEMBER, 1234, true)), null));

        assertEquals(Verdict.CONFIRMED, api.confirm(MEMBER, RESOURCE));
        assertEquals(2, requests.get());
    }

    @Test
    @DisplayName("A long, missing or unreadable rate limit is no answer, without asking again")
    void longRateLimitIsUnavailable() {
        answers.add(new Answer(429, "", "60000"));
        answers.add(new Answer(429, "", null));
        answers.add(new Answer(429, "", "soon"));

        assertEquals(Verdict.UNAVAILABLE, api.confirm(MEMBER, RESOURCE));
        assertEquals(Verdict.UNAVAILABLE, api.confirm(MEMBER, RESOURCE));
        assertEquals(Verdict.UNAVAILABLE, api.confirm(MEMBER, RESOURCE));
        assertEquals(3, requests.get());
    }

    @Test
    @DisplayName("An unreachable API is no answer")
    void unreachableIsUnavailable() {
        server.stop(0);

        assertEquals(Verdict.UNAVAILABLE, api.confirm(MEMBER, RESOURCE));
    }

    @Test
    @DisplayName("Without a token nobody is asked")
    void noneIsUnchecked() {
        assertEquals(Verdict.UNCHECKED, BuiltByBitApi.NONE.confirm(MEMBER, RESOURCE));
    }
}
