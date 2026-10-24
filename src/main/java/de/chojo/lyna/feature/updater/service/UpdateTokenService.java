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
import de.chojo.lyna.feature.updater.entity.DashboardSettings;
import de.chojo.lyna.feature.updater.entity.HeldKey;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.RaisedAllowance;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.entity.UpdateTokenRequest;
import de.chojo.lyna.feature.updater.repository.DashboardProductRepository;
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
 * Server keys: what an account may hold, and trading an upgrade token in.
 *
 * <p>Two kinds of key share these rules, told apart by {@link KeyKind}. An updater on somebody's server
 * holds an upgrade token and trades it for a download. Every trade replaces the token, so one token
 * buys one download: a copy taken off the server is worth a single download at most, and using it
 * locks the real updater out - which its owner sees, because the next trade of the token it still
 * holds is recorded as reuse. A dashboard key proves a server to the hosted dashboard and is replaced
 * on a schedule instead; {@link DashboardKeyService} handles its use.
 *
 * <p>Keys belong to an account and a product, never to a licence. Whether the account may download
 * the product is asked again on every use, by the same rule the storefront uses, so a licence, a
 * share and a role all work, and losing them stops the server without anybody revoking its key.
 *
 * <p>Free products are not offered upgrade tokens: anybody may download them from their public
 * address. Dashboard keys are offered by the products that switched them on, to buyers up to the
 * product's allowance and to an account that only took the trial exactly one, which it cannot ask to
 * raise.
 */
@Singleton
public class UpdateTokenService {
    /** Upgrade tokens an account may hold for one product before it has to ask for more. */
    public static final int DEFAULT_ALLOWANCE = 10;

    /** Dashboard keys an account that only took the product's trial may hold. */
    public static final int TRIAL_ALLOWANCE = 1;

    /** The most one request may ask for. */
    public static final int MAX_REQUEST = 100;

    public static final int MAX_NAME_LENGTH = 64;

    public static final int MAX_REASON_LENGTH = 500;

    /** How soon after a trade the next may follow, so a looping updater cannot hammer the repository. */
    public static final Duration MIN_TRADE_INTERVAL = Duration.ofSeconds(10);

    private static final SecureRandom RANDOM = new SecureRandom();

    private final UpdateTokenRepository tokens;
    private final UpdateTokenQuotaRepository quota;
    private final DashboardProductRepository dashboards;
    private final DownloadAccessService access;
    private final KioskProductRepository kiosk;
    private final Clock clock;

    /**
     * Why something was not done.
     */
    public enum Refusal {
        /** The account may not download the product, and holds no trial that would do instead. */
        NO_ACCESS,
        /** The product is free, and needs no token. */
        FREE,
        /** The product does not hand out keys of this kind. */
        NOT_OFFERED,
        /** The account holds as many keys as it is allowed. */
        LIMIT_REACHED,
        INVALID_NAME,
        INVALID_REQUEST,
        /** The account is still waiting on a request for this product. */
        PENDING,
        NOT_FOUND,
        /** A key nobody issued, or one of the other kind. */
        UNKNOWN,
        /** A key that was already given up for its successor. */
        REUSED,
        /** A dashboard key that went unused for longer than its product allows. */
        EXPIRED,
        /** The key was used for this less than {@link #MIN_TRADE_INTERVAL} ago. */
        TOO_SOON
    }

    /**
     * What an account holds of a product that decides how many dashboard keys it may have.
     */
    public enum Holding {
        /** Access to the product: a licence, a share or a role. */
        BOUGHT,
        /** No access, but the product's trial. */
        TRIAL,
        NONE
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
     * @param used    the keys of the kind the account holds for the product
     * @param allowed how many it may hold
     * @param trial   whether it holds only the product's trial, which allows one dashboard key and no
     *                request for more
     */
    public record Allowance(int productId, KeyKind kind, int used, int allowed, boolean trial) {}

    @Inject
    public UpdateTokenService(
            UpdateTokenRepository tokens,
            UpdateTokenQuotaRepository quota,
            DashboardProductRepository dashboards,
            DownloadAccessService access,
            KioskProductRepository kiosk) {
        this(tokens, quota, dashboards, access, kiosk, Clock.systemUTC());
    }

    public UpdateTokenService(
            UpdateTokenRepository tokens,
            UpdateTokenQuotaRepository quota,
            DashboardProductRepository dashboards,
            DownloadAccessService access,
            KioskProductRepository kiosk,
            Clock clock) {
        this.tokens = tokens;
        this.quota = quota;
        this.dashboards = dashboards;
        this.access = access;
        this.kiosk = kiosk;
        this.clock = clock;
    }

    /**
     * @return the account's keys of both kinds, by product, kind and name
     */
    public List<UpdateToken> tokens(int accountId) {
        return tokens.forAccount(accountId);
    }

    /**
     * Upgrade tokens are offered for every premium product the account may download; dashboard keys
     * for every product that offers them and that the account may download or took the trial of. A
     * product the account still holds keys for is listed either way, so they can be revoked.
     *
     * @return for every such product, how many keys of the kind the account holds and may hold
     */
    public List<Allowance> allowances(int accountId, KeyKind kind) {
        Set<Integer> products = new TreeSet<>(access.entitledProductIds(accountId));
        if (kind == KeyKind.UPDATE) {
            products.removeIf(kiosk::isFree);
        } else {
            products.addAll(dashboards.trialProductIds(accountId));
            products.removeIf(productId -> !dashboard(productId).enabled());
        }
        tokens.forAccount(accountId).stream()
                .filter(token -> token.kind() == kind)
                .forEach(token -> products.add(token.productId()));
        return products.stream()
                .map(productId -> allowance(accountId, productId, kind))
                .toList();
    }

    private Allowance allowance(int accountId, int productId, KeyKind kind) {
        boolean trial = kind == KeyKind.DASHBOARD && holding(accountId, productId) == Holding.TRIAL;
        return new Allowance(
                productId, kind, tokens.count(accountId, productId, kind), allowed(accountId, productId, kind), trial);
    }

    /**
     * @return how many keys of the kind the account may hold for the product
     */
    public int allowed(int accountId, int productId, KeyKind kind) {
        if (kind == KeyKind.UPDATE) return DEFAULT_ALLOWANCE + quota.granted(accountId, productId, kind);
        return switch (holding(accountId, productId)) {
            case BOUGHT -> dashboard(productId).allowance() + quota.granted(accountId, productId, kind);
            case TRIAL -> TRIAL_ALLOWANCE;
            case NONE -> 0;
        };
    }

    /**
     * Asked live, on every use of a dashboard key, so losing access stops it and regaining access
     * revives it.
     *
     * @return what the account holds of the product
     */
    public Holding holding(int accountId, int productId) {
        if (!releaseTypes(accountId, productId).isEmpty()) return Holding.BOUGHT;
        return dashboards.tookTrial(accountId, productId) ? Holding.TRIAL : Holding.NONE;
    }

    /**
     * @return how the product hands out dashboard keys; switched off for a product that does not exist
     */
    public DashboardSettings dashboard(int productId) {
        return dashboards.settings(productId).orElse(DashboardSettings.DEFAULT);
    }

    /**
     * Changes how a product of the guild hands out dashboard keys.
     */
    public Outcome<DashboardSettings> configureDashboard(long guildId, int productId, DashboardSettings settings) {
        if (!settings.valid()) return Outcome.refused(Refusal.INVALID_REQUEST);
        if (!dashboards.set(guildId, productId, settings)) return Outcome.refused(Refusal.NOT_FOUND);
        return Outcome.of(settings);
    }

    /**
     * @return the release types the account may download of the product, as the names the RELEASE_TYPE
     *     enum uses
     */
    public Set<String> releaseTypes(int accountId, int productId) {
        return access.releaseTypes(accountId, productId);
    }

    public Outcome<UpdateToken> create(int accountId, int productId, KeyKind kind, String name) {
        Optional<String> cleaned = name(name);
        if (cleaned.isEmpty()) return Outcome.refused(Refusal.INVALID_NAME);
        Refusal refusal = offered(accountId, productId, kind);
        if (refusal != null) return Outcome.refused(refusal);
        if (tokens.count(accountId, productId, kind) >= allowed(accountId, productId, kind)) {
            return Outcome.refused(Refusal.LIMIT_REACHED);
        }
        return Outcome.of(tokens.create(kind, accountId, productId, cleaned.get(), generate(kind)));
    }

    /**
     * @return why the account may not have keys of the kind for the product at all, or null when it may
     */
    private @Nullable Refusal offered(int accountId, int productId, KeyKind kind) {
        if (kind == KeyKind.UPDATE) {
            if (kiosk.isFree(productId)) return Refusal.FREE;
            return releaseTypes(accountId, productId).isEmpty() ? Refusal.NO_ACCESS : null;
        }
        if (!dashboard(productId).enabled()) return Refusal.NOT_OFFERED;
        return holding(accountId, productId) == Holding.NONE ? Refusal.NO_ACCESS : null;
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
     * Gives an entry a new key of its kind, for a server that lost the one it had or let it expire.
     */
    public Outcome<UpdateToken> reissue(int accountId, int id) {
        Optional<UpdateToken> entry = tokens.byId(id).filter(token -> token.accountId() == accountId);
        if (entry.isEmpty()) return Outcome.refused(Refusal.NOT_FOUND);
        return Outcome.of(tokens.reissue(id, accountId, generate(entry.get().kind())), Refusal.NOT_FOUND);
    }

    /**
     * Finds the entry an upgrade token belongs to, before anything is fetched for it. Nothing is spent
     * here; a caller that goes on to deliver something calls {@link #trade} with the same token.
     *
     * <p>A token already traded in is refused as {@link Refusal#REUSED} and recorded on its entry. A
     * dashboard key is not an upgrade token and is refused as {@link Refusal#UNKNOWN} unlooked-at.
     */
    public Outcome<UpdateToken> resolve(@Nullable String token) {
        if (!KeyKind.UPDATE.marks(token)) return Outcome.refused(Refusal.UNKNOWN);
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
     * Spends an upgrade token. The entry gets a new one, which is the only token that works from now
     * on.
     *
     * <p>Two trades of one token at the same moment cannot both succeed; the loser is refused as
     * {@link Refusal#REUSED}.
     *
     * @return the entry, carrying its new token
     */
    public Outcome<UpdateToken> trade(String token) {
        if (!KeyKind.UPDATE.marks(token)) return Outcome.refused(Refusal.UNKNOWN);
        Optional<UpdateToken> rotated = tokens.rotate(token, generate(KeyKind.UPDATE));
        return rotated.isPresent() ? Outcome.of(rotated.get()) : notCurrent(token);
    }

    /**
     * Asks for more keys of a kind on a product than the account is allowed. An account that only took
     * the trial cannot ask for more dashboard keys.
     */
    public Outcome<UpdateTokenRequest> requestMore(
            int accountId, int productId, KeyKind kind, int amount, String reason) {
        String cleaned = reason == null ? "" : reason.strip();
        if (amount < 1 || amount > MAX_REQUEST || cleaned.isEmpty() || cleaned.length() > MAX_REASON_LENGTH) {
            return Outcome.refused(Refusal.INVALID_REQUEST);
        }
        Refusal refusal = offered(accountId, productId, kind);
        if (refusal != null) return Outcome.refused(refusal);
        if (kind == KeyKind.DASHBOARD && holding(accountId, productId) != Holding.BOUGHT) {
            return Outcome.refused(Refusal.NO_ACCESS);
        }
        return Outcome.of(quota.request(accountId, productId, kind, amount, cleaned), Refusal.PENDING);
    }

    /**
     * @return the account's requests of both kinds, newest first
     */
    public List<UpdateTokenRequest> requests(int accountId) {
        return quota.requestsOf(accountId);
    }

    /**
     * @return the requests for keys of the kind waiting on the guild's products, oldest first
     */
    public List<UpdateTokenRequest> pending(long guildId, KeyKind kind) {
        return quota.pendingInGuild(guildId, kind);
    }

    /**
     * Approves or declines a pending request on one of the guild's products. Approving raises the
     * account's allowance of the kind asked for by what it asked for.
     *
     * @param decidedBy the Discord id of whoever decided, null when not known
     */
    public Outcome<UpdateTokenRequest> decide(long guildId, int requestId, boolean approve, @Nullable Long decidedBy) {
        return Outcome.of(quota.decide(guildId, requestId, approve, decidedBy), Refusal.NOT_FOUND);
    }

    /**
     * @return the accounts allowed more keys of the kind than the default on the guild's products
     */
    public List<RaisedAllowance> raised(long guildId, KeyKind kind) {
        return quota.raisedInGuild(guildId, kind);
    }

    /**
     * Sets how many keys of a kind an account may hold on a product of the guild beyond the default.
     * Lowering it revokes nothing.
     *
     * @return whether the product belongs to the guild
     */
    public boolean setGranted(long guildId, int accountId, int productId, KeyKind kind, int granted) {
        return quota.setGranted(guildId, accountId, productId, kind, granted);
    }

    /**
     * @return the keys of the kind held for the guild's products, without the keys themselves
     */
    public List<HeldKey> held(long guildId, KeyKind kind) {
        return tokens.inGuild(guildId, kind);
    }

    /**
     * Ends an entry of the kind on one of the guild's products, whoever holds it.
     *
     * @return whether there was such an entry
     */
    public boolean revokeInGuild(long guildId, int id, KeyKind kind) {
        return tokens.deleteInGuild(guildId, id, kind);
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

    /**
     * @return a fresh key of the kind: its prefix and 32 random bytes
     */
    static String generate(KeyKind kind) {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return kind.prefix() + Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }
}
