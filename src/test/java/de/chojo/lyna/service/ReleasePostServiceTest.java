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
import net.dv8tion.jda.api.entities.Guild;
import net.dv8tion.jda.api.entities.MessageEmbed;
import net.dv8tion.jda.api.entities.channel.middleman.GuildMessageChannel;
import net.dv8tion.jda.api.requests.restaction.MessageCreateAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.nio.charset.StandardCharsets;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.HexFormat;
import java.util.List;
import java.util.Optional;
import java.util.function.Consumer;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
    private final Gateway gateway = mock(Gateway.class);
    private final GuildMessageChannel channel = mock(GuildMessageChannel.class);
    private final MessageCreateAction send = mock(MessageCreateAction.class);
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
        Guild guild = mock(Guild.class);
        when(gateway.guilds()).thenReturn(List.of(guild));
        when(guild.getChannelById(GuildMessageChannel.class, 42L)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(send);
        Api api = mock(Api.class);
        when(api.url()).thenReturn("https://lyna.example");
        service = new ReleasePostService(webhooks, products, gateway, api);
    }

    private MessageEmbed announce(String body) throws Exception {
        var webhook = service.issue(productId);
        webhooks.channel(productId, 42L);
        assertEquals(Outcome.ANNOUNCED, deliver(webhook.token(), "release", sign(webhook.secret(), body), body));
        ArgumentCaptor<MessageEmbed> embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(channel).sendMessageEmbeds(embed.capture());
        return embed.getValue();
    }

    @Test
    @DisplayName("The announcement names the product and tag, links the release and the product page")
    void announcement() throws Exception {
        MessageEmbed embed = announce(RELEASE.formatted("released"));

        assertEquals("BloodNight v1.2.3", embed.getTitle());
        assertEquals("https://github.com/x/y/releases/v1.2.3", embed.getUrl());
        assertEquals("Fixes", embed.getDescription());
        assertEquals("Spooky", embed.getAuthor().getName());
        assertEquals("x/y", embed.getFooter().getText());
        assertEquals("Stable", embed.getFields().get(0).getValue());
        assertEquals(
                "https://lyna.example/products/" + productId,
                embed.getFields().get(1).getValue());
    }

    @Test
    @DisplayName("Long patch notes are cut to what Discord takes, and a bare release adds nothing empty")
    void bareRelease() throws Exception {
        String body =
                "{\"action\": \"prereleased\", \"release\": {\"tag_name\": \"v2\", \"name\": \"v2\", \"body\": \"%s\", \"prerelease\": true}}"
                        .formatted("x".repeat(5000));

        MessageEmbed embed = announce(body);

        assertEquals(MessageEmbed.DESCRIPTION_MAX_LENGTH, embed.getDescription().length());
        assertNull(embed.getAuthor());
        assertNull(embed.getFooter());
        assertEquals("Pre-release", embed.getFields().get(0).getValue());
    }

    @Test
    @DisplayName("A post Discord refuses is logged rather than thrown")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void refusedPost() throws Exception {
        announce(RELEASE.formatted("released"));

        ArgumentCaptor<Consumer> success = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(send).queue(success.capture(), failure.capture());
        assertDoesNotThrow(() -> success.getValue().accept(null));
        assertDoesNotThrow(() -> failure.getValue().accept(new RuntimeException("missing access")));
    }

    @Test
    @DisplayName("Without a channel the bot can post in, the release is acknowledged and nothing is sent")
    void noChannel() throws Exception {
        var webhook = service.issue(productId);
        String body = RELEASE.formatted("released");

        assertEquals(Outcome.ANNOUNCED, deliver(webhook.token(), "release", sign(webhook.secret(), body), body));
        webhooks.channel(productId, 99L);
        assertEquals(Outcome.ANNOUNCED, deliver(webhook.token(), "release", sign(webhook.secret(), body), body));

        verify(channel, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("Removing the webhook ends it, once")
    void removal() {
        service.issue(productId);

        assertTrue(webhooks.remove(productId));
        assertFalse(webhooks.remove(productId));
        assertTrue(webhooks.ofProduct(productId).isEmpty());
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
        assertFalse(webhooks.channel(productId, 42L));
        service.issue(productId);
        assertTrue(webhooks.channel(productId, 42L));
        assertTrue(webhooks.channel(productId, 0L));
        assertEquals(0L, webhooks.ofProduct(productId).orElseThrow().channelId());
    }
}
