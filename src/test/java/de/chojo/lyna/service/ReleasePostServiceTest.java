/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.releasepost.repository.ReleaseWebhookRepository;
import de.chojo.lyna.feature.releasepost.service.ReleasePostService;
import de.chojo.lyna.feature.releasepost.service.ReleasePostService.Outcome;
import de.chojo.lyna.gateway.Gateway;
import de.chojo.lyna.repository.RepositoryTestBase;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.Optional;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * What a GitHub delivery to a product's release webhook leads to.
 */
class ReleasePostServiceTest extends RepositoryTestBase {
    private static final String RELEASE = """
            {"action": "%s", "release": {"tag_name": "v1.2.3", "name": "Spooky", "body": "Fixes", "html_url": "https://github.com/x/y/releases/v1.2.3", "prerelease": false},
             "repository": {"full_name": "x/y"}}
            """;

    private final ReleaseWebhookRepository webhooks = new ReleaseWebhookRepository();
    private ReleasePostService service;
    private int productId;

    @BeforeEach
    void setUp() throws SQLException {
        clear("release_webhook", "product");
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO product (guild_id, name, role, free) VALUES (1, 'BloodNight', 1, TRUE) RETURNING id")) {
            rows.next();
            productId = rows.getInt(1);
        }
        Product product = mock(Product.class);
        when(product.id()).thenReturn(productId);
        when(product.name()).thenReturn("BloodNight");
        ProductLookup products = mock(ProductLookup.class);
        when(products.byId(productId)).thenReturn(Optional.of(product));
        service = new ReleasePostService(webhooks, products, Gateway.NONE, mock(Api.class));
    }

    private static String sign(String secret, String body) throws Exception {
        Mac mac = Mac.getInstance("HmacSHA256");
        mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
        return "sha256=" + HexFormat.of().formatHex(mac.doFinal(body.getBytes(StandardCharsets.UTF_8)));
    }

    private Outcome deliver(String token, String event, String signature, String body) {
        return service.receive(token, event, signature, body.getBytes(StandardCharsets.UTF_8));
    }

    @Test
    @DisplayName("A signed release is announced; edits and drafts are acknowledged and dropped")
    void signedReleaseIsAnnounced() throws Exception {
        var webhook = service.issue(productId);

        for (String action : new String[] {"released", "prereleased"}) {
            String body = RELEASE.formatted(action);
            assertEquals(Outcome.ANNOUNCED, deliver(webhook.token(), "release", sign(webhook.secret(), body), body));
        }
        for (String action : new String[] {"edited", "created", "deleted"}) {
            String body = RELEASE.formatted(action);
            assertEquals(Outcome.IGNORED, deliver(webhook.token(), "release", sign(webhook.secret(), body), body));
        }
    }

    @Test
    @DisplayName("Nothing unsigned or wrongly signed is acted on")
    void unsignedIsRefused() throws Exception {
        var webhook = service.issue(productId);
        String body = RELEASE.formatted("released");

        assertEquals(Outcome.FORBIDDEN, deliver(webhook.token(), "release", null, body));
        assertEquals(Outcome.FORBIDDEN, deliver(webhook.token(), "release", "sha256=00", body));
        assertEquals(Outcome.FORBIDDEN, deliver(webhook.token(), "release", "sha256=nothex", body));
        assertEquals(Outcome.FORBIDDEN, deliver(webhook.token(), "release", sign("other", body), body));
        assertEquals(Outcome.FORBIDDEN, deliver(webhook.token(), "release", sign(webhook.secret(), body), body + " "));
    }

    @Test
    @DisplayName("GitHub's ping is answered, other events are dropped, unknown addresses are unknown")
    void pingAndOthers() throws Exception {
        var webhook = service.issue(productId);

        assertEquals(Outcome.PONG, deliver(webhook.token(), "ping", sign(webhook.secret(), "{}"), "{}"));
        assertEquals(Outcome.IGNORED, deliver(webhook.token(), "push", sign(webhook.secret(), "{}"), "{}"));
        assertEquals(Outcome.IGNORED, deliver(webhook.token(), "release", sign(webhook.secret(), "nope"), "nope"));
        assertEquals(Outcome.UNKNOWN, deliver("nope", "ping", sign(webhook.secret(), "{}"), "{}"));
    }

    @Test
    @DisplayName("Issuing again replaces the address and secret, keeps the channel, and the old address stops working")
    void rotation() throws Exception {
        var first = service.issue(productId);
        webhooks.channel(productId, 42L);
        var second = service.issue(productId);

        assertNotEquals(first.token(), second.token());
        assertNotEquals(first.secret(), second.secret());
        assertEquals(42L, second.channelId());
        assertEquals(Outcome.UNKNOWN, deliver(first.token(), "ping", sign(first.secret(), "{}"), "{}"));
    }

    @Test
    @DisplayName("A channel can be cleared, and only a product with a webhook has one to set")
    void channel() {
        assertTrue(!webhooks.channel(productId, 42L));
        service.issue(productId);
        assertTrue(webhooks.channel(productId, 42L));
        assertTrue(webhooks.channel(productId, 0L));
        assertEquals(0L, webhooks.ofProduct(productId).orElseThrow().channelId());
    }
}
