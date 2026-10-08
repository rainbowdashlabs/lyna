/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.trial.service;

import com.google.inject.Singleton;
import org.slf4j.Logger;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * Mail providers that hand out throwaway addresses, which do not count towards a web trial: an
 * address made for one sign-up proves nothing about who is behind it.
 *
 * <p>The list is the public {@code disposable-email-domains} list (CC0). A copy ships in the jar, so
 * the check works offline from the first start; {@link #refresh()} replaces it with the current list,
 * and a refresh that fails keeps whatever was there.
 */
@Singleton
public class DisposableEmailDomains {
    static final String SOURCE =
            "https://raw.githubusercontent.com/disposable-email-domains/disposable-email-domains/main/disposable_email_blocklist.conf";
    private static final Logger log = getLogger(DisposableEmailDomains.class);

    private final HttpClient http =
            HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    private final URI source;
    private volatile Set<String> domains;

    public DisposableEmailDomains() {
        this(URI.create(SOURCE));
    }

    DisposableEmailDomains(URI source) {
        this.source = source;
        try (InputStream bundled = getClass().getClassLoader().getResourceAsStream("disposable-email-domains.txt")) {
            domains = bundled == null ? Set.of() : parse(new String(bundled.readAllBytes(), StandardCharsets.UTF_8));
        } catch (IOException e) {
            domains = Set.of();
        }
    }

    /**
     * @return whether the address belongs to a throwaway provider, or to a subdomain of one
     */
    public boolean isDisposable(String address) {
        int at = address.lastIndexOf('@');
        String domain = (at < 0 ? address : address.substring(at + 1)).strip().toLowerCase(Locale.ROOT);
        while (!domain.isEmpty()) {
            if (domains.contains(domain)) return true;
            int dot = domain.indexOf('.');
            if (dot < 0) return false;
            domain = domain.substring(dot + 1);
        }
        return false;
    }

    /**
     * Fetches the current list. Keeps the one it has when the fetch fails or answers nothing usable.
     */
    public void refresh() {
        try {
            HttpResponse<String> response = http.send(
                    HttpRequest.newBuilder(source)
                            .timeout(Duration.ofSeconds(30))
                            .build(),
                    HttpResponse.BodyHandlers.ofString());
            Set<String> fetched = response.statusCode() == 200 ? parse(response.body()) : Set.of();
            if (fetched.size() < 100) {
                log.debug(
                        "Kept the throwaway mail list; the refresh answered {} with {} domains",
                        response.statusCode(),
                        fetched.size());
                return;
            }
            domains = fetched;
        } catch (IOException e) {
            log.debug("Kept the throwaway mail list; the refresh failed", e);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    int size() {
        return domains.size();
    }

    private static Set<String> parse(String list) {
        return list.lines()
                .map(line -> line.strip().toLowerCase(Locale.ROOT))
                .filter(line -> !line.isEmpty() && !line.startsWith("#"))
                .collect(Collectors.toUnmodifiableSet());
    }
}
