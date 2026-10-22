/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasepost.service;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.configuration.elements.Api;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.product.entity.Product;
import de.chojo.lyna.feature.product.repository.ProductLinksRepository;
import de.chojo.lyna.feature.product.repository.ProductLookup;
import de.chojo.lyna.feature.releasenotes.entity.GithubRelease;
import de.chojo.lyna.feature.releasenotes.service.ReleaseNotesService;
import de.chojo.lyna.feature.releasepost.repository.ReleaseWebhookRepository;
import de.chojo.lyna.feature.releasepost.repository.ReleaseWebhookRepository.ReleaseWebhook;
import de.chojo.lyna.gateway.Gateway;
import net.dv8tion.jda.api.EmbedBuilder;
import net.dv8tion.jda.api.components.actionrow.ActionRow;
import net.dv8tion.jda.api.components.buttons.Button;
import net.dv8tion.jda.api.entities.MessageEmbed;
import org.slf4j.Logger;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.function.BiFunction;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

import static org.slf4j.LoggerFactory.getLogger;

/**
 * What a product's GitHub webhook posts in Discord: its releases in the release channel, and pushes to
 * the default branch, pull requests and issues in the activity channel.
 *
 * <p>A release announcement carries a button to the product's page on Lyna, where it is downloaded.
 * Activity carries none: it is for people following the project, not for buyers.
 *
 * <p>GitHub signs every delivery with the webhook's secret, and nothing unsigned is acted on: the
 * address alone is not enough to make the bot post in somebody's channel. Only published releases and
 * pre-releases are announced; drafts, edits and deletions are not, and neither is any activity the
 * product switched off. Every release event, announced or not, also updates the release notes the
 * product's page shows.
 */
@Singleton
public class GithubWebhookService {
    private static final Logger log = getLogger(GithubWebhookService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final HexFormat HEX = HexFormat.of();
    private static final Set<String> ANNOUNCED = Set.of("released", "prereleased");
    private static final int DESCRIPTION_LIMIT = MessageEmbed.DESCRIPTION_MAX_LENGTH;

    private final ReleaseWebhookRepository webhooks;
    private final ProductLookup products;
    private final ProductLinksRepository productLinks;
    private final Gateway gateway;
    private final Api api;
    private final KioskProductRepository kiosk;
    private final ReleaseNotesService releaseNotes;
    private final ObjectMapper json = new ObjectMapper();

    public enum Outcome {
        /** No webhook has this address. */
        UNKNOWN,
        /** The signature is missing or wrong. */
        FORBIDDEN,
        /** GitHub checking the hook works. */
        PONG,
        /** Signed, but not something that is announced. */
        IGNORED,
        /** Announced, or would have been with a channel and a bot. */
        ANNOUNCED
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Payload(String action, Release release, Repository repository) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Release(
            long id,
            @JsonProperty("tag_name") String tag,
            String name,
            String body,
            @JsonProperty("html_url") String url,
            boolean prerelease,
            boolean draft,
            @JsonProperty("published_at") String publishedAt) {
        /**
         * @return the release as its notes are kept, or null when GitHub gave it no publication date
         */
        GithubRelease kept() {
            if (publishedAt == null || url == null || tag == null) return null;
            try {
                return new GithubRelease(
                        id,
                        tag,
                        name == null || name.isBlank() ? null : name,
                        body == null || body.isBlank() ? null : body,
                        url,
                        prerelease,
                        Instant.parse(publishedAt));
            } catch (DateTimeParseException e) {
                return null;
            }
        }
    }

    @JsonIgnoreProperties(ignoreUnknown = true)
    record Repository(@JsonProperty("full_name") String fullName) {}

    @Inject
    public GithubWebhookService(
            ReleaseWebhookRepository webhooks,
            ProductLookup products,
            ProductLinksRepository productLinks,
            Gateway gateway,
            Api api,
            KioskProductRepository kiosk,
            ReleaseNotesService releaseNotes) {
        this.kiosk = kiosk;
        this.releaseNotes = releaseNotes;
        this.webhooks = webhooks;
        this.products = products;
        this.productLinks = productLinks;
        this.gateway = gateway;
        this.api = api;
    }

    /**
     * Gives a product a webhook address and secret, replacing any it had.
     */
    public ReleaseWebhook issue(int productId) {
        webhooks.issue(productId, randomHex(24), randomHex(32));
        return webhooks.ofProduct(productId).orElseThrow();
    }

    /**
     * @param event     the {@code X-GitHub-Event} header
     * @param signature the {@code X-Hub-Signature-256} header
     */
    public Outcome receive(String token, String event, String signature, byte[] body) {
        Optional<ReleaseWebhook> webhook = webhooks.byToken(token);
        if (webhook.isEmpty()) return Outcome.UNKNOWN;
        if (!signed(webhook.get().secret(), signature, body)) return Outcome.FORBIDDEN;
        if (event == null) return Outcome.IGNORED;
        return switch (event) {
            case "ping" -> Outcome.PONG;
            case "release" -> release(webhook.get(), body);
            case "push" ->
                activity(
                        webhook.get(),
                        body,
                        PushPost.Payload.class,
                        (product, payload) ->
                                webhook.get().activity().push() ? PushPost.embed(product, payload) : Optional.empty());
            case "pull_request" ->
                activity(
                        webhook.get(),
                        body,
                        PullRequestPost.Payload.class,
                        (product, payload) -> PullRequestPost.embed(
                                product, payload, webhook.get().activity()));
            case "issues" ->
                activity(
                        webhook.get(),
                        body,
                        IssuePost.Payload.class,
                        (product, payload) ->
                                IssuePost.embed(product, payload, webhook.get().activity()));
            default -> Outcome.IGNORED;
        };
    }

    private Outcome release(ReleaseWebhook webhook, byte[] body) {
        Optional<Payload> payload = read(body, Payload.class);
        if (payload.isEmpty() || payload.get().release() == null) return Outcome.IGNORED;
        keepNotes(webhook, payload.get());
        if (!ANNOUNCED.contains(payload.get().action())) return Outcome.IGNORED;
        announce(webhook, payload.get());
        return Outcome.ANNOUNCED;
    }

    /**
     * Hands every release event to the product's release notes, edits and deletions included, which
     * are not announced but do change what the product page shows.
     */
    private void keepNotes(ReleaseWebhook webhook, Payload payload) {
        Release release = payload.release();
        kiosk.byId(webhook.productId())
                .ifPresent(product -> releaseNotes.received(
                        product.url(),
                        payload.repository() == null
                                ? null
                                : payload.repository().fullName(),
                        payload.action(),
                        release.id(),
                        release.draft(),
                        release.kept()));
    }

    /**
     * Posts a push, pull request or issue in the product's activity channel, when the event is one
     * that is posted at all.
     *
     * <p>The post carries embeds only and mentions nobody. Titles and descriptions are written by
     * whoever opened the issue, and a channel post must not become a way to ping a role.
     */
    private <T> Outcome activity(
            ReleaseWebhook webhook, byte[] body, Class<T> type, BiFunction<String, T, Optional<MessageEmbed>> format) {
        Optional<T> payload = read(body, type);
        if (payload.isEmpty()) return Outcome.IGNORED;
        Optional<Product> product = products.byId(webhook.productId());
        if (product.isEmpty()) return Outcome.IGNORED;
        Optional<MessageEmbed> embed = format.apply(product.get().name(), payload.get());
        if (embed.isEmpty()) return Outcome.IGNORED;
        Optional<ChannelTarget> target = ChannelTarget.resolve(gateway.guilds(), webhook.activityChannelId());
        if (target.isEmpty()) {
            log.debug("Activity of product {} not posted: no channel the bot can post in", webhook.productId());
            return Outcome.ANNOUNCED;
        }
        target.get()
                .post(
                        embed.get().getTitle(),
                        embed.get(),
                        List.of(),
                        error -> log.warn("Could not post activity of product {}", webhook.productId(), error));
        return Outcome.ANNOUNCED;
    }

    private <T> Optional<T> read(byte[] body, Class<T> type) {
        try {
            return Optional.ofNullable(json.readValue(body, type));
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private void announce(ReleaseWebhook webhook, Payload payload) {
        Optional<Product> product = products.byId(webhook.productId());
        Optional<ChannelTarget> target = ChannelTarget.resolve(gateway.guilds(), webhook.channelId());
        if (product.isEmpty() || target.isEmpty()) {
            log.debug("Release of product {} not announced: no channel the bot can post in", webhook.productId());
            return;
        }
        target.get()
                .post(
                        "%s %s"
                                .formatted(
                                        product.get().name(), payload.release().tag()),
                        embed(product.get(), payload),
                        List.of(ActionRow.of(buttons(product.get()))),
                        error -> log.warn("Could not announce a release of product {}", webhook.productId(), error));
    }

    /**
     * The way to the product page, where the release is downloaded, and to the product's wiki when it
     * has one.
     */
    private List<Button> buttons(Product product) {
        List<Button> buttons = new ArrayList<>();
        buttons.add(Button.link("%s/products/%d".formatted(api.url(), product.id()), "Download on Lyna"));
        String wiki = productLinks.get(product.id()).wikiUrl();
        if (wiki != null) buttons.add(Button.link(wiki, "Wiki"));
        return buttons;
    }

    MessageEmbed embed(Product product, Payload payload) {
        Release release = payload.release();
        String title = "%s %s".formatted(product.name(), release.tag());
        String body = Objects.requireNonNullElse(release.body(), "").strip();
        EmbedBuilder embed = new EmbedBuilder()
                .setTitle(
                        title.length() > MessageEmbed.TITLE_MAX_LENGTH
                                ? title.substring(0, MessageEmbed.TITLE_MAX_LENGTH)
                                : title,
                        release.url())
                .setDescription(
                        body.length() > DESCRIPTION_LIMIT ? body.substring(0, DESCRIPTION_LIMIT - 1) + "…" : body)
                .addField("Release", release.prerelease() ? "Pre-release" : "Stable", true)
                .setTimestamp(Instant.now());
        if (release.name() != null
                && !release.name().isBlank()
                && !release.name().equals(release.tag())) {
            embed.setAuthor(release.name());
        }
        if (payload.repository() != null) embed.setFooter(payload.repository().fullName());
        return embed.build();
    }

    static boolean signed(String secret, String signature, byte[] body) {
        if (signature == null || !signature.startsWith("sha256=")) return false;
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256"));
            byte[] expected = mac.doFinal(body);
            byte[] given = HEX.parseHex(signature.substring("sha256=".length()));
            return MessageDigest.isEqual(expected, given);
        } catch (GeneralSecurityException | IllegalArgumentException e) {
            return false;
        }
    }

    private static String randomHex(int bytes) {
        byte[] random = new byte[bytes];
        RANDOM.nextBytes(random);
        return HEX.formatHex(random);
    }
}
