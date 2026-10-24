/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.service;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.BuiltByBit;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitCall;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitKeyRequest;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitListing;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitOutcome;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitPurchase;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitLogRepository;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitLogRepository.Received;
import de.chojo.lyna.feature.builtbybit.repository.BuiltByBitProductRepository;
import de.chojo.lyna.feature.license.entity.License;
import de.chojo.lyna.feature.license.entity.LicenseSource;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.purchase.service.PurchaseService;
import org.jetbrains.annotations.Nullable;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Map;
import java.util.Optional;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * What BuiltByBit asks of Lyna: a purchase to record, and a key to write into a download.
 *
 * <p>Both end at the same licence. It is issued against the buyer's member id, so whichever of the two
 * calls arrives first makes it and the other finds it. Neither call is signed; a shared secret says it
 * came from BuiltByBit, and with an API token BuiltByBit is additionally asked before anything is
 * issued. Every call carrying the right secret is logged with what came of it.
 */
@Singleton
public class BuiltByBitService {
    private static final Logger log = getLogger(BuiltByBitService.class);

    private final BuiltByBit config;
    private final BuiltByBitProductRepository listings;
    private final BuiltByBitLogRepository calls;
    private final ProductLookup products;
    private final PurchaseService purchases;
    private final BuiltByBitApi api;
    private final ObjectMapper json = new ObjectMapper();

    @Inject
    public BuiltByBitService(
            BuiltByBit config,
            BuiltByBitProductRepository listings,
            BuiltByBitLogRepository calls,
            ProductLookup products,
            PurchaseService purchases,
            BuiltByBitApi api) {
        this.config = config;
        this.listings = listings;
        this.calls = calls;
        this.products = products;
        this.purchases = purchases;
        this.api = api;
    }

    /**
     * @return whether the purchase webhook was called at the address carrying the right secret
     */
    public boolean webhookSecretMatches(@Nullable String presented) {
        return matches(presented, config.webhookSecret());
    }

    /**
     * @return whether the key placeholder sent the right secret
     */
    public boolean keySecretMatches(@Nullable String presented) {
        return matches(presented, config.keySecret());
    }

    /**
     * Records a purchase the webhook reported and issues the licence for it.
     *
     * @param raw the body as received, which should be the template the setup guide gives
     */
    public BuiltByBitOutcome receivePurchase(String raw) {
        Optional<BuiltByBitPurchase> parsed = parsePurchase(raw);
        if (parsed.isEmpty()) {
            calls.record(new Received(BuiltByBitCall.PURCHASE, null, null, null, null, raw), BuiltByBitOutcome.INVALID);
            return BuiltByBitOutcome.INVALID;
        }
        BuiltByBitPurchase purchase = parsed.get();
        BuiltByBitListing listing = purchase.listing();
        Optional<Product> product = productFor(listing);
        Received received = new Received(
                BuiltByBitCall.PURCHASE,
                purchase.memberId(),
                listing.resourceId(),
                listing.addonId(),
                product.map(Product::id).orElse(null),
                raw);
        BuiltByBitOutcome outcome;
        if (purchase.bundle()) {
            outcome = BuiltByBitOutcome.BUNDLE;
        } else if (product.isEmpty()) {
            outcome = BuiltByBitOutcome.UNKNOWN_PRODUCT;
        } else {
            outcome = issue(product.get(), purchase.memberId(), listing).outcome();
        }
        calls.record(received, outcome);
        log.info("BuiltByBit purchase of {} by member {}: {}", listing, purchase.memberId(), outcome);
        return outcome;
    }

    /**
     * Answers the key placeholder with the key of the downloader's licence, issuing it if no purchase
     * has been reported yet.
     *
     * @param form the form fields BuiltByBit sent, the secret already checked
     * @param raw  the body as received
     */
    public KeyAnswer requestKey(Map<String, String> form, String raw) {
        Optional<BuiltByBitKeyRequest> parsed = parseKeyRequest(form);
        if (parsed.isEmpty()) {
            calls.record(new Received(BuiltByBitCall.KEY, null, null, null, null, raw), BuiltByBitOutcome.INVALID);
            return new KeyAnswer(BuiltByBitOutcome.INVALID, null);
        }
        BuiltByBitKeyRequest request = parsed.get();
        BuiltByBitListing listing = BuiltByBitListing.resource(request.resourceId());
        Optional<Product> product = productFor(listing);
        Received received = new Received(
                BuiltByBitCall.KEY,
                request.memberId(),
                listing.resourceId(),
                listing.addonId(),
                product.map(Product::id).orElse(null),
                raw);
        KeyAnswer answer = product.map(found -> issue(found, request.memberId(), listing))
                .orElse(new KeyAnswer(BuiltByBitOutcome.UNKNOWN_PRODUCT, null));
        calls.record(received, answer.outcome());
        log.info(
                "BuiltByBit key for {} version {} by member {}: {}",
                listing,
                request.versionNumber(),
                request.memberId(),
                answer.outcome());
        return answer;
    }

    private KeyAnswer issue(Product product, long memberId, BuiltByBitListing listing) {
        BuiltByBitOutcome refusal =
                switch (api.confirm(memberId, listing)) {
                    case CONFIRMED, UNCHECKED -> null;
                    case DENIED -> BuiltByBitOutcome.DENIED;
                    case UNAVAILABLE -> BuiltByBitOutcome.API_UNAVAILABLE;
                };
        if (refusal != null) return new KeyAnswer(refusal, null);
        Optional<License> license =
                purchases.issueUnaddressed(product, String.valueOf(memberId), LicenseSource.BUILTBYBIT);
        return license.map(issued -> new KeyAnswer(BuiltByBitOutcome.ISSUED, issued.key()))
                .orElse(new KeyAnswer(BuiltByBitOutcome.INVALID, null));
    }

    private Optional<Product> productFor(BuiltByBitListing listing) {
        return listings.productFor(listing).flatMap(products::byId);
    }

    private Optional<BuiltByBitPurchase> parsePurchase(String raw) {
        JsonNode body;
        try {
            body = json.readTree(raw);
        } catch (IOException e) {
            return Optional.empty();
        }
        if (body == null || !body.isObject()) return Optional.empty();
        Optional<Long> member = id(body.path("user_id").asText(""));
        Optional<Long> resource = id(body.path("resource_id").asText(""));
        if (member.isEmpty() || resource.isEmpty()) return Optional.empty();
        long addon = id(body.path("addon_id").asText("")).orElse(BuiltByBitListing.NO_ADDON);
        boolean bundle = id(body.path("bundle_id").asText("")).isPresent();
        return Optional.of(new BuiltByBitPurchase(
                member.get(), body.path("username").asText(""), new BuiltByBitListing(resource.get(), addon), bundle));
    }

    private Optional<BuiltByBitKeyRequest> parseKeyRequest(Map<String, String> form) {
        Optional<Long> member = id(form.getOrDefault("user_id", ""));
        Optional<Long> resource = id(form.getOrDefault("resource_id", ""));
        if (member.isEmpty() || resource.isEmpty()) return Optional.empty();
        return Optional.of(
                new BuiltByBitKeyRequest(member.get(), resource.get(), form.getOrDefault("version_number", "")));
    }

    /**
     * @return a BuiltByBit id, which is a positive number; anything else, an empty value or the zero
     *     BuiltByBit fills an unused one with, is none
     */
    private static Optional<Long> id(String value) {
        try {
            long id = Long.parseLong(value.trim());
            return id > 0 ? Optional.of(id) : Optional.empty();
        } catch (NumberFormatException e) {
            return Optional.empty();
        }
    }

    private static boolean matches(@Nullable String presented, String expected) {
        if (presented == null) return false;
        return MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), expected.getBytes(StandardCharsets.UTF_8));
    }

    /**
     * What the key placeholder is answered.
     *
     * @param key the licence key, present exactly when the outcome is {@link BuiltByBitOutcome#ISSUED}
     */
    public record KeyAnswer(
            BuiltByBitOutcome outcome, @Nullable String key) {}
}
