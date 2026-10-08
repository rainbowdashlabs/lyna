/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.trial.service;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Which addresses come from throwaway mail providers.
 */
class DisposableEmailDomainsTest {

    @Test
    @DisplayName("The bundled list knows the well-known providers and their subdomains, and not ordinary ones")
    void bundled() {
        DisposableEmailDomains domains = new DisposableEmailDomains();

        assertTrue(domains.isDisposable("someone@mailinator.com"));
        assertTrue(domains.isDisposable("someone@inbox.mailinator.com"));
        assertTrue(domains.isDisposable(" Someone@MAILINATOR.COM "));
        assertFalse(domains.isDisposable("someone@gmail.com"));
        assertFalse(domains.isDisposable("someone@localhost"));
        assertTrue(domains.isDisposable("mailinator.com"), "a bare domain is read as one");
        assertFalse(domains.isDisposable("nobody@"));
    }

    @Test
    @DisplayName("A refresh interrupted keeps the list and leaves the thread marked as interrupted")
    void interruptedRefresh() {
        DisposableEmailDomains domains = new DisposableEmailDomains(URI.create("http://127.0.0.1:1/list"));
        int before = domains.size();

        Thread.currentThread().interrupt();
        domains.refresh();

        assertTrue(Thread.interrupted());
        assertEquals(before, domains.size());
    }

    @Test
    @DisplayName("A refresh replaces the list; one that fails or answers too little keeps it")
    void refresh() throws IOException {
        AtomicReference<String> list = new AtomicReference<>("# comment\n" + "throwaway.example\n".repeat(1));
        AtomicReference<Integer> status = new AtomicReference<>(200);
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/list", exchange -> {
            byte[] body = list.get().getBytes(StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(status.get(), body.length);
            exchange.getResponseBody().write(body);
            exchange.close();
        });
        server.start();
        try {
            DisposableEmailDomains domains = new DisposableEmailDomains(URI.create(
                    "http://127.0.0.1:%d/list".formatted(server.getAddress().getPort())));
            int bundled = domains.size();

            domains.refresh();
            assertEquals(bundled, domains.size(), "a list of one domain is not taken");

            StringBuilder many = new StringBuilder("throwaway.example\n");
            for (int i = 0; i < 150; i++) many.append("junk").append(i).append(".example\n");
            list.set(many.toString());
            domains.refresh();
            assertEquals(151, domains.size());
            assertTrue(domains.isDisposable("a@throwaway.example"));
            assertFalse(domains.isDisposable("a@mailinator.com"), "the refreshed list replaced the bundled one");

            status.set(500);
            domains.refresh();
            assertEquals(151, domains.size(), "a failed refresh keeps the list");
        } finally {
            server.stop(0);
        }
        DisposableEmailDomains unreachable = new DisposableEmailDomains(URI.create("http://127.0.0.1:1/list"));
        int before = unreachable.size();
        unreachable.refresh();
        assertEquals(before, unreachable.size());
    }
}
