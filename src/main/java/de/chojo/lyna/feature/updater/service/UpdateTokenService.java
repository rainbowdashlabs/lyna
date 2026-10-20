/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.download.service.DownloadAccessService;
import de.chojo.lyna.feature.kiosk.repository.KioskProductRepository;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.repository.UpdateTokenQuotaRepository;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import org.jetbrains.annotations.Nullable;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.TreeSet;

/**
 * Upgrade tokens: what an account may hold, and trading one in.
 *
 * <p>An updater on somebody's server holds an upgrade token and trades it for a download. Every trade
 * replaces the token, so one token buys one download: a copy taken off the server is worth a single
 * download at most, and using it locks the real updater out - which its owner sees, because the next
 * trade of the token it still holds is recorded as reuse.
 *
 * <p>Tokens belong to an account and a product, never to a licence. Whether the account may download
 * the product is asked again on every trade, by the same rule the storefront uses, so a licence, a
 * share and a role all work, and losing them stops the updater without anybody revoking it.
 *
 * <p>Free products are not offered tokens: anybody may download them from their public address.
 */
@Singleton
public class UpdateTokenService {
    /** Tokens an account may hold for one product before it has to ask for more. */
    public static final int DEFAULT_ALLOWANCE = 10;

    /** The most one request may ask for. */
    public static final int MAX_REQUEST = 100;

    public static final int MAX_NAME_LENGTH = 64;

    public static final int MAX_REASON_LENGTH = 500;

    /** How soon after a trade the next may follow, so a looping updater cannot hammer the repository. */
    public static final Duration MIN_TRADE_INTERVAL = Duration.ofSeconds(10);

    private static final String PREFIX = "lyu_";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final UpdateTokenRepository tokens;
    private final UpdateTokenQuotaRepository quota;
    private final DownloadAccessService access;
    private final KioskProductRepository kiosk;
    private final Clock clock;

    /**
     * Why something was not done.
     */
    public enum Refusal {
        /** The account may not download the product. */
        NO_ACCESS,
        /** The product is free, and needs no token. */
        FREE,
        /** The account holds as many tokens as it is allowed. */
        LIMIT_REACHED,
        INVALID_NAME,
        INVALID_REQUEST,
        /** The account is still waiting on a request for this product. */
        PENDING,
        NOT_FOUND,
        /** A token nobody issued. */
        UNKNOWN,
        /** A token that was already traded in. */
        REUSED,
        /** The token was traded less than {@link #MIN_TRADE_INTERVAL} ago. */
        TOO_SOON
    }

    /**
     * What came of an attempt: either a value or the reason there is none.
     */
    public record Outcome<T>(@Nullable T value, @Nullable Refusal refusal) {
        public static <T> Outcome<T> of(T value) {
            return new Outcome<>(value, null);
        }

        public static <T> Outcome<T> refused(Refusal refusal) {
            return new Outcome<>(null, refusal);
        }

        public static <T> Outcome<T> of(Optional<T> value, Refusal otherwise) {
            return value.map(Outcome::of).orElseGet(() -> refused(otherwise));
        }

        public boolean ok() {
            return refusal == null;
        }
    }

    /**
     * @param used    the tokens the account holds for the product
     * @param allowed how many it may hold
     */
    public record Allowance(int productId, int used, int allowed) {}

    @Inject
    public UpdateTokenService(
            UpdateTokenRepository tokens,
            UpdateTokenQuotaRepository quota,
            DownloadAccessService access,
            KioskProductRepository kiosk) {
        this(tokens, quota, access, kiosk, Clock.systemUTC());
    }

    public UpdateTokenService(
            UpdateTokenRepository tokens,
            UpdateTokenQuotaRepository quota,
            DownloadAccessService access,
            KioskProductRepository kiosk,
            Clock clock) {
        this.tokens = tokens;
        this.quota = quota;
        this.access = access;
        this.kiosk = kiosk;
        this.clock = clock;
    }

    /**
     * @return the account's tokens, by product and name
     */
    public List<UpdateToken> tokens(int accountId) {
        return tokens.forAccount(accountId);
    }

    /**
     * @return for every premium product the account may download or holds tokens for, how many it holds
     *     and may hold
     */
    public List<Allowance> allowances(int accountId) {
        Set<Integer> products = new TreeSet<>(access.entitledProductIds(accountId));
        products.removeIf(kiosk::isFree);
        tokens.forAccount(accountId).forEach(token -> products.add(token.productId()));
        return products.stream()
                .map(productId ->
                        new Allowance(productId, tokens.count(accountId, productId), allowed(accountId, productId)))
                .toList();
    }

    public int allowed(int accountId, int productId) {
        return DEFAULT_ALLOWANCE + quota.granted(accountId, productId);
    }

    /**
     * @return the release types the account may download of the product, as the names the RELEASE_TYPE
     *     enum uses
     */
    public Set<String> releaseTypes(int accountId, int productId) {
        return access.releaseTypes(accountId, productId);
    }

    public Outcome<UpdateToken> create(int accountId, int productId, String name) {
        Optional<String> cleaned = name(name);
        if (cleaned.isEmpty()) return Outcome.refused(Refusal.INVALID_NAME);
        if (kiosk.isFree(productId)) return Outcome.refused(Refusal.FREE);
        if (releaseTypes(accountId, productId).isEmpty()) return Outcome.refused(Refusal.NO_ACCESS);
        if (tokens.count(accountId, productId) >= allowed(accountId, productId)) {
            return Outcome.refused(Refusal.LIMIT_REACHED);
        }
        return Outcome.of(tokens.create(accountId, productId, cleaned.get(), generate()));
    }

    public Outcome<UpdateToken> rename(int accountId, int id, String name) {
        Optional<String> cleaned = name(name);
        if (cleaned.isEmpty()) return Outcome.refused(Refusal.INVALID_NAME);
        if (!tokens.rename(id, accountId, cleaned.get())) return Outcome.refused(Refusal.NOT_FOUND);
        return Outcome.of(tokens.byId(id), Refusal.NOT_FOUND);
    }

    /**
     * @return whether the account had that entry
     */
    public boolean revoke(int accountId, int id) {
        return tokens.delete(id, accountId);
    }

    /**
     * Gives an entry a new token, for an updater that lost the one it had.
     */
    public Outcome<UpdateToken> reissue(int accountId, int id) {
        return Outcome.of(tokens.reissue(id, accountId, generate()), Refusal.NOT_FOUND);
    }

    /**
     * Finds the entry a token belongs to, before anything is fetched for it. Nothing is spent here; a
     * caller that goes on to deliver something calls {@link #trade} with the same token.
     *
     * <p>A token already traded in is refused as {@link Refusal#REUSED} and recorded on its entry.
     */
    public Outcome<UpdateToken> resolve(@Nullable String token) {
        if (token == null || token.isBlank()) return Outcome.refused(Refusal.UNKNOWN);
        Optional<UpdateToken> entry = tokens.byToken(token);
        if (entry.isEmpty()) return notCurrent(token);
        UpdateToken found = entry.get();
        if (found.lastUsedAt() != null
                && found.lastUsedAt().plus(MIN_TRADE_INTERVAL).isAfter(clock.instant())) {
            return Outcome.refused(Refusal.TOO_SOON);
        }
        if (!kiosk.isFree(found.productId())
                && releaseTypes(found.accountId(), found.productId()).isEmpty()) {
            return Outcome.refused(Refusal.NO_ACCESS);
        }
        return Outcome.of(found);
    }

    /**
     * Spends a token. The entry gets a new one, which is the only token that works from now on.
     *
     * <p>Two trades of one token at the same moment cannot both succeed; the loser is refused as
     * {@link Refusal#REUSED}.
     *
     * @return the entry, carrying its new token
     */
    public Outcome<UpdateToken> trade(String token) {
        Optional<UpdateToken> rotated = tokens.rotate(token, generate());
        return rotated.isPresent() ? Outcome.of(rotated.get()) : notCurrent(token);
    }

    /**
     * Asks for more tokens on a product than the account is allowed.
     */
    public Outcome<UpdateTokenRequest> requestMore(int accountId, int productId, int amount, String reason) {
        String cleaned = reason == null ? "" : reason.strip();
        if (amount < 1 || amount > MAX_REQUEST || cleaned.isEmpty() || cleaned.length() > MAX_REASON_LENGTH) {
            return Outcome.refused(Refusal.INVALID_REQUEST);
        }
        if (kiosk.isFree(productId)) return Outcome.refused(Refusal.FREE);
        if (releaseTypes(accountId, productId).isEmpty()) return Outcome.refused(Refusal.NO_ACCESS);
        return Outcome.of(quota.request(accountId, productId, amount, cleaned), Refusal.PENDING);
    }

    /**
     * @return the account's requests, newest first
     */
    public List<UpdateTokenRequest> requests(int accountId) {
        return quota.requestsOf(accountId);
    }

    /**
     * @return the requests waiting on the guild's products, oldest first
     */
    public List<UpdateTokenRequest> pending(long guildId) {
        return quota.pendingInGuild(guildId);
    }

    /**
     * Approves or declines a pending request on one of the guild's products. Approving raises the
     * account's allowance by what it asked for.
     *
     * @param decidedBy the Discord id of whoever decided, null when not known
     */
    public Outcome<UpdateTokenRequest> decide(long guildId, int requestId, boolean approve, @Nullable Long decidedBy) {
        return Outcome.of(quota.decide(guildId, requestId, approve, decidedBy), Refusal.NOT_FOUND);
    }

    private Outcome<UpdateToken> notCurrent(String token) {
        return tokens.markReused(token).isPresent()
                ? Outcome.refused(Refusal.REUSED)
                : Outcome.refused(Refusal.UNKNOWN);
    }

    private static Optional<String> name(@Nullable String name) {
        if (name == null) return Optional.empty();
        String cleaned = name.strip();
        if (cleaned.isEmpty() || cleaned.length() > MAX_NAME_LENGTH) return Optional.empty();
        return Optional.of(cleaned);
    }

    private static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return PREFIX + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
