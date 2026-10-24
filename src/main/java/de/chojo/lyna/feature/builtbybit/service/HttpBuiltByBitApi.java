/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.configuration.elements.BuiltByBit;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;
import org.slf4j.Logger;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Clock;
import java.time.Duration;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * BuiltByBit's API, asked through {@code GET /v2/resources/creator/licenses}.
 *
 * <p>A licence is in force when it is permanent and active, or when it is temporary and the present
 * lies between its start and end. A rate-limited request is retried once after the wait BuiltByBit
 * names, as long as that wait is short; anything else that is not an answer counts as no answer.
 */
public class HttpBuiltByBitApi implements BuiltByBitApi {
    private static final Logger log = getLogger(HttpBuiltByBitApi.class);
    private static final long LONGEST_WAIT_MILLIS = 5_000;

    private final BuiltByBit config;
    private final HttpClient http;
    private final Clock clock;
    private final ObjectMapper json = new ObjectMapper();

    public HttpBuiltByBitApi(BuiltByBit config, Clock clock) {
        this.config = config;
        this.clock = clock;
        this.http =
                HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
    }

    /**
     * {@inheritDoc}
     *
     * <p>TODO: an add-on is matched by its add-on id as the licence's content id; check that against a
     * real add-on purchase.
     */
    @Override
    public Verdict confirm(long memberId, BuiltByBitListing listing) {
        try {
            HttpResponse<String> response = send(memberId, listing);
            if (response.statusCode() == 429 && waitFor(response)) {
                response = send(memberId, listing);
            }
            if (response.statusCode() / 100 != 2) {
                log.warn("BuiltByBit answered HTTP {}: {}", response.statusCode(), response.body());
                return Verdict.UNAVAILABLE;
            }
            return holds(json.readTree(response.body()), memberId, listing) ? Verdict.CONFIRMED : Verdict.DENIED;
        } catch (IOException e) {
            log.warn("BuiltByBit could not be asked", e);
            return Verdict.UNAVAILABLE;
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Verdict.UNAVAILABLE;
        }
    }

    private HttpResponse<String> send(long memberId, BuiltByBitListing listing)
            throws IOException, InterruptedException {
        String url = "%s/v2/resources/creator/licenses?resource_ids=%d&buyer_ids=%d"
                .formatted(config.apiUrl().replaceAll("/+$", ""), listing.resourceId(), memberId);
        HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                .header("Authorization", "Token " + config.apiToken())
                .header("Accept", "application/json")
                .timeout(Duration.ofSeconds(10))
                .GET()
                .build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Waits as long as BuiltByBit asked, which it names in milliseconds.
     *
     * @return whether it was worth waiting, rather than too long to hold a webhook open for
     */
    private boolean waitFor(HttpResponse<String> response) throws InterruptedException {
        long millis = response.headers()
                .firstValue("Retry-After")
                .map(HttpBuiltByBitApi::millis)
                .orElse(LONGEST_WAIT_MILLIS + 1);
        if (millis > LONGEST_WAIT_MILLIS) return false;
        Thread.sleep(millis);
        return true;
    }

    private static long millis(String value) {
        try {
            return Long.parseLong(value.trim());
        } catch (NumberFormatException e) {
            return LONGEST_WAIT_MILLIS + 1;
        }
    }

    private boolean holds(JsonNode body, long memberId, BuiltByBitListing listing) {
        long expected = listing.isAddon() ? listing.addonId() : listing.resourceId();
        long now = clock.instant().getEpochSecond();
        for (JsonNode license : body.path("data").path("licenses")) {
            if (license.path("buyer_id").asLong(-1) != memberId) continue;
            if (license.path("content_id").asLong(-1) != expected) continue;
            if (inForce(license, now)) return true;
        }
        return false;
    }

    private static boolean inForce(JsonNode license, long now) {
        if (license.path("permanent").asBoolean(false)) {
            return license.path("active").asBoolean(false);
        }
        return license.path("start_date").asLong(Long.MAX_VALUE) <= now
                && now <= license.path("end_date").asLong(Long.MIN_VALUE);
    }
}
