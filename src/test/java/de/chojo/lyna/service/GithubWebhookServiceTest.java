/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.releasepost.repository.ReleaseWebhookRepository;
import de.chojo.lyna.feature.releasepost.service.GithubWebhookService;
import de.chojo.lyna.feature.releasepost.service.GithubWebhookService.Outcome;
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
class GithubWebhookServiceTest extends RepositoryTestBase {
    private static final String RELEASE = """
            {"action": "%s", "release": {"tag_name": "v1.2.3", "name": "Spooky", "body": "Fixes", "html_url": "https://github.com/x/y/releases/v1.2.3", "prerelease": false},
             "repository": {"full_name": "x/y"}}
            """;

    private final ReleaseWebhookRepository webhooks = new ReleaseWebhookRepository();
    private final ProductLinksRepository productLinks = new ProductLinksRepository();
    private final Gateway gateway = mock(Gateway.class);
    private final GuildMessageChannel channel = mock(GuildMessageChannel.class);
    private final MessageCreateAction send = mock(MessageCreateAction.class);
    private final GuildMessageChannel activityChannel = mock(GuildMessageChannel.class);
    private final MessageCreateAction activitySend = mock(MessageCreateAction.class);
    private GithubWebhookService service;
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
        when(guild.getGuildChannelById(42L)).thenReturn(channel);
        when(channel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(send);
        when(guild.getGuildChannelById(77L)).thenReturn(activityChannel);
        when(activityChannel.sendMessageEmbeds(any(MessageEmbed.class))).thenReturn(activitySend);
        when(activitySend.setAllowedMentions(any())).thenReturn(activitySend);
        when(send.addComponents(any(java.util.Collection.class))).thenReturn(send);
        when(send.setAllowedMentions(any())).thenReturn(send);
        Api api = mock(Api.class);
        when(api.url()).thenReturn("https://lyna.example");
        service = new GithubWebhookService(webhooks, products, productLinks, gateway, api);
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
        @SuppressWarnings("unchecked")
        ArgumentCaptor<java.util.Collection<net.dv8tion.jda.api.components.actionrow.ActionRow>> rows =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(send).addComponents(rows.capture());
        verify(send).setAllowedMentions(List.of());
        assertEquals(
                "https://lyna.example/products/" + productId,
                ((net.dv8tion.jda.api.components.buttons.Button) rows.getValue()
                                .iterator()
                                .next()
                                .getComponents()
                                .getFirst())
                        .getUrl());
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
        assertEquals(Outcome.IGNORED, deliver(webhook.token(), "star", sign(webhook.secret(), "{}"), "{}"));
        assertEquals(Outcome.IGNORED, deliver(webhook.token(), null, sign(webhook.secret(), "{}"), "{}"));
        assertEquals(Outcome.IGNORED, deliver(webhook.token(), "issues", sign(webhook.secret(), "nope"), "nope"));
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

    private static final String PUSH = """
            {"ref": "refs/heads/%s", "deleted": %s, "compare": "https://github.com/x/y/compare/a...b",
             "commits": [%s],
             "repository": {"full_name": "x/y", "default_branch": "main"}}
            """;

    private static final String PULL_REQUEST = """
            {"action": "%s", "number": 7,
             "pull_request": {"title": "Spook harder", "html_url": "https://github.com/x/y/pull/7", "body": "%s",
                              "merged": %s, "user": {"login": "ada"}, "merged_by": {"login": "nora"}},
             "repository": {"full_name": "x/y"}}
            """;

    private static final String ISSUE = """
            {"action": "%s",
             "issue": {"number": 9, "title": "Moon too bright", "html_url": "https://github.com/x/y/issues/9",
                       "body": "It glows", "user": {"login": "ada"}, "state_reason": %s},
             "repository": {"full_name": "x/y"}}
            """;

    private static String commits(int count) {
        return java.util.stream.IntStream.range(0, count)
                .mapToObj(i -> """
                                                 {"id": "abcdef%d000000", "message": "Commit %d\\n\\nbody", "url": "https://github.com/x/y/commit/%d",
                                                  "author": {"name": "Ada"}}""".formatted(i, i, i))
                .collect(java.util.stream.Collectors.joining(","));
    }

    private String hookWithActivity(ReleaseWebhookRepository.Activity activity) {
        var webhook = service.issue(productId);
        webhooks.activity(productId, 77L, activity);
        return webhook.token();
    }

    private Outcome activity(String event, String body) {
        var webhook = webhooks.ofProduct(productId).orElseThrow();
        try {
            return deliver(webhook.token(), event, sign(webhook.secret(), body), body);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    private MessageEmbed posted() {
        ArgumentCaptor<MessageEmbed> embed = ArgumentCaptor.forClass(MessageEmbed.class);
        verify(activityChannel).sendMessageEmbeds(embed.capture());
        verify(activitySend).setAllowedMentions(List.of());
        verify(channel, never()).sendMessageEmbeds(any(MessageEmbed.class));
        return embed.getValue();
    }

    @Test
    @DisplayName("A push to the default branch lists its commits in the activity channel, with no download button")
    void pushIsPosted() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);

        assertEquals(Outcome.ANNOUNCED, activity("push", PUSH.formatted("main", false, commits(7))));

        MessageEmbed embed = posted();
        assertEquals("7 new commits to main", embed.getTitle());
        assertEquals("https://github.com/x/y/compare/a...b", embed.getUrl());
        assertEquals("BloodNight", embed.getAuthor().getName());
        assertEquals("x/y", embed.getFooter().getText());
        assertTrue(embed.getDescription().startsWith("[`abcdef0`](https://github.com/x/y/commit/0) Commit 0 - Ada"));
        assertTrue(embed.getDescription().endsWith("and 2 more"));
        verify(activitySend, never())
                .addComponents(any(net.dv8tion.jda.api.components.MessageTopLevelComponent[].class));
    }

    @Test
    @DisplayName("One commit is a commit, not commits")
    void singleCommit() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);

        activity("push", PUSH.formatted("main", false, commits(1)));

        assertEquals("1 new commit to main", posted().getTitle());
    }

    @Test
    @DisplayName("Pushes elsewhere than the default branch, deletions, empty pushes and tags are not posted")
    void otherPushesAreDropped() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);

        assertEquals(Outcome.IGNORED, activity("push", PUSH.formatted("feature/x", false, commits(1))));
        assertEquals(Outcome.IGNORED, activity("push", PUSH.formatted("main", true, commits(1))));
        assertEquals(Outcome.IGNORED, activity("push", PUSH.formatted("main", false, "")));
        assertEquals(
                Outcome.IGNORED,
                activity("push", PUSH.formatted("main", false, commits(1)).replace("refs/heads/main", "refs/tags/v1")));
        assertEquals(Outcome.IGNORED, activity("push", "{\"ref\": \"refs/heads/main\"}"));
        verify(activityChannel, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("A switched-off push is acknowledged and not posted")
    void pushSwitchedOff() {
        hookWithActivity(new ReleaseWebhookRepository.Activity(false, true, true, true, true));

        assertEquals(Outcome.IGNORED, activity("push", PUSH.formatted("main", false, commits(1))));
        verify(activityChannel, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("An opened pull request is posted with its description and author")
    void pullRequestOpened() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);

        assertEquals(
                Outcome.ANNOUNCED, activity("pull_request", PULL_REQUEST.formatted("opened", "x".repeat(800), false)));

        MessageEmbed embed = posted();
        assertEquals("PR #7 opened: Spook harder", embed.getTitle());
        assertEquals("https://github.com/x/y/pull/7", embed.getUrl());
        assertEquals(500, embed.getDescription().length());
        assertEquals("ada", embed.getFields().getFirst().getValue());
    }

    @Test
    @DisplayName("A merged pull request says who merged it; one closed unmerged is not posted")
    void pullRequestMerged() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);

        assertEquals(Outcome.IGNORED, activity("pull_request", PULL_REQUEST.formatted("closed", "", false)));
        assertEquals(Outcome.IGNORED, activity("pull_request", PULL_REQUEST.formatted("edited", "", false)));
        assertEquals(Outcome.ANNOUNCED, activity("pull_request", PULL_REQUEST.formatted("closed", "", true)));

        MessageEmbed embed = posted();
        assertEquals("PR #7 merged: Spook harder", embed.getTitle());
        assertEquals("nora", embed.getFields().get(1).getValue());
    }

    @Test
    @DisplayName("Switched-off pull request posts are dropped, and a payload without a pull request too")
    void pullRequestSwitchedOff() {
        hookWithActivity(new ReleaseWebhookRepository.Activity(true, false, false, true, true));

        assertEquals(Outcome.IGNORED, activity("pull_request", PULL_REQUEST.formatted("opened", "", false)));
        assertEquals(Outcome.IGNORED, activity("pull_request", PULL_REQUEST.formatted("closed", "", true)));
        assertEquals(Outcome.IGNORED, activity("pull_request", "{\"action\": \"opened\"}"));
    }

    @Test
    @DisplayName("Issues opened and closed are posted, closed ones with why")
    void issues() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);

        assertEquals(Outcome.ANNOUNCED, activity("issues", ISSUE.formatted("opened", "null")));
        MessageEmbed opened = posted();
        assertEquals("Issue #9 opened: Moon too bright", opened.getTitle());
        assertEquals("It glows", opened.getDescription());

        for (String[] reason : new String[][] {
            {"\"not_planned\"", "Not planned"}, {"\"duplicate\"", "Duplicate"}, {"\"completed\"", "Completed"}
        }) {
            org.mockito.Mockito.clearInvocations(activityChannel, activitySend);
            assertEquals(Outcome.ANNOUNCED, activity("issues", ISSUE.formatted("closed", reason[0])));
            MessageEmbed closed = posted();
            assertEquals("Issue #9 closed: Moon too bright", closed.getTitle());
            assertEquals(reason[1], closed.getFields().getFirst().getValue());
        }
        assertEquals(Outcome.IGNORED, activity("issues", ISSUE.formatted("labeled", "null")));
    }

    @Test
    @DisplayName("Switched-off issue posts are dropped, and a payload without an issue too")
    void issuesSwitchedOff() {
        hookWithActivity(new ReleaseWebhookRepository.Activity(true, true, true, false, false));

        assertEquals(Outcome.IGNORED, activity("issues", ISSUE.formatted("opened", "null")));
        assertEquals(Outcome.IGNORED, activity("issues", ISSUE.formatted("closed", "null")));
        assertEquals(Outcome.IGNORED, activity("issues", "{\"action\": \"opened\"}"));
    }

    @Test
    @DisplayName("Without an activity channel, activity is acknowledged and nothing is sent")
    void noActivityChannel() {
        service.issue(productId);

        assertEquals(Outcome.ANNOUNCED, activity("issues", ISSUE.formatted("opened", "null")));
        verify(activityChannel, never()).sendMessageEmbeds(any(MessageEmbed.class));
    }

    @Test
    @DisplayName("A post Discord refuses is logged rather than thrown")
    @SuppressWarnings({"unchecked", "rawtypes"})
    void refusedActivity() {
        hookWithActivity(ReleaseWebhookRepository.Activity.ALL);
        activity("issues", ISSUE.formatted("opened", "null"));

        ArgumentCaptor<Consumer> success = ArgumentCaptor.forClass(Consumer.class);
        ArgumentCaptor<Consumer> failure = ArgumentCaptor.forClass(Consumer.class);
        verify(activitySend).queue(success.capture(), failure.capture());
        assertDoesNotThrow(() -> success.getValue().accept(null));
        assertDoesNotThrow(() -> failure.getValue().accept(new RuntimeException("missing access")));
    }

    @Test
    @DisplayName("Activity of a product that is gone is dropped")
    void productGone() throws Exception {
        int other;
        try (var connection = dataSource.getConnection();
                Statement statement = connection.createStatement();
                var rows = statement.executeQuery(
                        "INSERT INTO product (guild_id, name, role) VALUES (1, 'Ghost', 1) RETURNING id")) {
            rows.next();
            other = rows.getInt(1);
        }
        var webhook = service.issue(other);
        String body = ISSUE.formatted("opened", "null");

        assertEquals(Outcome.IGNORED, deliver(webhook.token(), "issues", sign(webhook.secret(), body), body));
    }

    @Test
    @DisplayName("The activity channel and switches are saved, and only a product with a webhook has them")
    void activitySettings() {
        var off = new ReleaseWebhookRepository.Activity(false, true, false, true, false);
        assertFalse(webhooks.activity(productId, 77L, off));
        service.issue(productId);

        assertEquals(
                ReleaseWebhookRepository.Activity.ALL,
                webhooks.ofProduct(productId).orElseThrow().activity());
        assertTrue(webhooks.activity(productId, 77L, off));

        var stored = webhooks.ofProduct(productId).orElseThrow();
        assertEquals(77L, stored.activityChannelId());
        assertEquals(off, stored.activity());
        assertTrue(webhooks.activity(productId, 0L, off));
        assertEquals(0L, webhooks.ofProduct(productId).orElseThrow().activityChannelId());
    }

    @Test
    @DisplayName("Text is cut with an ellipsis, a missing text is empty, and a commit's subject is its first line")
    void embedText() {
        assertEquals("", de.chojo.lyna.feature.releasepost.service.GithubEmbeds.cut(null, 5));
        assertEquals("abcd…", de.chojo.lyna.feature.releasepost.service.GithubEmbeds.cut("abcdefgh", 5));
        assertEquals("", de.chojo.lyna.feature.releasepost.service.GithubEmbeds.firstLine(null));
        assertEquals("Subject", de.chojo.lyna.feature.releasepost.service.GithubEmbeds.firstLine("Subject \nbody"));
    }

    @Test
    @DisplayName("A product with a wiki gets a button to it beside the download, one without does not")
    @SuppressWarnings("unchecked")
    void wikiButton() throws Exception {
        productLinks.set(
                productId, new de.chojo.lyna.feature.product.entity.ProductLinks("https://example.invalid/wiki", null));

        announce(RELEASE.formatted("released"));

        ArgumentCaptor<java.util.Collection<net.dv8tion.jda.api.components.actionrow.ActionRow>> rows =
                ArgumentCaptor.forClass(java.util.Collection.class);
        verify(send).addComponents(rows.capture());
        var buttons = rows.getValue().iterator().next().getComponents();
        assertEquals(2, buttons.size());
        assertEquals("Wiki", ((net.dv8tion.jda.api.components.buttons.Button) buttons.get(1)).getLabel());
        assertEquals(
                "https://example.invalid/wiki",
                ((net.dv8tion.jda.api.components.buttons.Button) buttons.get(1)).getUrl());
    }
}
