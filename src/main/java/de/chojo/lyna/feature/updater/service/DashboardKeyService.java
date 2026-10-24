/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.feature.updater.entity.KeyKind;
import de.chojo.lyna.feature.updater.entity.RetiredKey;
import de.chojo.lyna.feature.updater.entity.UpdateToken;
import de.chojo.lyna.feature.updater.repository.UpdateTokenRepository;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Holding;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Outcome;
import de.chojo.lyna.feature.updater.service.UpdateTokenService.Refusal;
import org.jetbrains.annotations.Nullable;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

/**
 * Using a dashboard key: a server refreshing it, and the hosted gateway verifying it.
 *
 * <p>A key is traded for its successor once it is older than the product's refresh interval, the way
 * an upgrade token is traded for a download: in one conditional statement, so of two refreshes at the
 * same moment one rotates and the other finds the key already retired. Not issuing a successor is
 * what locks a server out - by revoking its entry, by the account losing the product, or by the
 * product switching dashboard keys off.
 *
 * <p>A retired key is still answered for {@link #GRACE} after it was retired, with the entry's current
 * key. That makes a refresh safe to repeat - a server that lost the answer, or a second node of one
 * network still holding the old key, gets the new one - and lets a server that connected just before a
 * refresh stay connected. After the grace a retired key is refused, and its presentation recorded as
 * reuse, since only a copy would still be using it.
 *
 * <p>A key unused for longer than the product's expiry interval expires, and only a reissue in the
 * account area brings its entry back. Every refresh and every verification counts as use.
 */
@Singleton
public class DashboardKeyService {
    /** How long a retired key is still answered with its successor. */
    public static final Duration GRACE = Duration.ofMinutes(10);

    /** How soon after a refresh the next may follow, so a looping server cannot hammer Lyna. */
    public static final Duration MIN_REFRESH_INTERVAL = UpdateTokenService.MIN_TRADE_INTERVAL;

    private final UpdateTokenRepository tokens;
    private final UpdateTokenService service;
    private final Clock clock;

    /**
     * @param entry   the entry, carrying the key the server is to hold from now on
     * @param rotated whether that key is a different one from the key presented
     */
    public record Refreshed(UpdateToken entry, boolean rotated) {}

    /**
     * @param entry   the entry the key belongs to
     * @param holding whether its account bought the product or only took the trial
     * @param rotated whether the key presented was already retired, inside the grace
     */
    public record Verified(UpdateToken entry, Holding holding, boolean rotated) {}

    /**
     * @param graced whether the key presented is a retired one, answered for its entry inside the grace
     */
    private record Located(UpdateToken entry, boolean graced) {}

    @Inject
    public DashboardKeyService(UpdateTokenRepository tokens, UpdateTokenService service) {
        this(tokens, service, Clock.systemUTC());
    }

    public DashboardKeyService(UpdateTokenRepository tokens, UpdateTokenService service, Clock clock) {
        this.tokens = tokens;
        this.service = service;
        this.clock = clock;
    }

    /**
     * Answers a server asking for the key it should hold: the same one until it is due for a refresh,
     * then its successor.
     *
     * <p>Refused as {@link Refusal#UNKNOWN} for a key nobody issued, {@link Refusal#REUSED} for one
     * retired longer than the grace ago, {@link Refusal#EXPIRED}, {@link Refusal#NOT_OFFERED} when the
     * product switched dashboard keys off, {@link Refusal#NO_ACCESS} when the account holds neither the
     * product nor its trial, and {@link Refusal#TOO_SOON} when the same server refreshed moments ago.
     */
    public Outcome<Refreshed> refresh(@Nullable String key) {
        Outcome<Located> located = locate(key);
        if (!located.ok()) return Outcome.refused(located.refusal());
        UpdateToken entry = located.value().entry();
        Refusal standing = standing(entry).refusal();
        if (standing != null) return Outcome.refused(standing);
        if (located.value().graced()) return Outcome.of(new Refreshed(touch(entry, false), true));
        Instant now = clock.instant();
        if (entry.refreshedAt() != null
                && entry.refreshedAt().plus(MIN_REFRESH_INTERVAL).isAfter(now)) {
            return Outcome.refused(Refusal.TOO_SOON);
        }
        if (entry.refreshAt() != null && entry.refreshAt().isAfter(now)) {
            return Outcome.of(new Refreshed(touch(entry, true), false));
        }
        Optional<UpdateToken> rotated = tokens.rotate(key, UpdateTokenService.generate(KeyKind.DASHBOARD));
        return rotated.map(next -> Outcome.of(new Refreshed(next, true))).orElseGet(() -> refresh(key));
    }

    /**
     * Answers the gateway asking whether a key may use the dashboard now, and notes the use.
     *
     * <p>Refused for the same reasons as {@link #refresh}, except that verifying is never too soon.
     */
    public Outcome<Verified> verify(@Nullable String key) {
        Outcome<Located> located = locate(key);
        if (!located.ok()) return Outcome.refused(located.refusal());
        UpdateToken entry = located.value().entry();
        Outcome<Holding> standing = standing(entry);
        if (!standing.ok()) return Outcome.refused(standing.refusal());
        return Outcome.of(new Verified(
                touch(entry, false), standing.value(), located.value().graced()));
    }

    /**
     * Finds the entry a dashboard key belongs to: the one whose current key it is, or the one that
     * retired it inside the grace. A key retired before that is recorded as reused.
     */
    private Outcome<Located> locate(@Nullable String key) {
        if (!KeyKind.DASHBOARD.marks(key)) return Outcome.refused(Refusal.UNKNOWN);
        Optional<UpdateToken> current = tokens.byToken(key).filter(entry -> entry.kind() == KeyKind.DASHBOARD);
        if (current.isPresent()) return Outcome.of(new Located(current.get(), false));
        Optional<RetiredKey> retired = tokens.retired(key).filter(old -> old.kind() == KeyKind.DASHBOARD);
        if (retired.isEmpty()) return Outcome.refused(Refusal.UNKNOWN);
        if (!retired.get().retiredAt().plus(GRACE).isAfter(clock.instant())) {
            tokens.markReused(key);
            return Outcome.refused(Refusal.REUSED);
        }
        return Outcome.of(tokens.byId(retired.get().entryId()).map(entry -> new Located(entry, true)), Refusal.UNKNOWN);
    }

    /**
     * Whether the entry may use the dashboard now: not expired, its product still offering dashboard
     * keys, and its account holding the product or its trial.
     *
     * @return what the account holds of the product
     */
    private Outcome<Holding> standing(UpdateToken entry) {
        if (entry.expiresAt() != null && !entry.expiresAt().isAfter(clock.instant())) {
            return Outcome.refused(Refusal.EXPIRED);
        }
        if (!service.dashboard(entry.productId()).enabled()) return Outcome.refused(Refusal.NOT_OFFERED);
        Holding holding = service.holding(entry.accountId(), entry.productId());
        return holding == Holding.NONE ? Outcome.refused(Refusal.NO_ACCESS) : Outcome.of(holding);
    }

    private UpdateToken touch(UpdateToken entry, boolean refreshed) {
        return tokens.touch(entry.id(), refreshed).orElse(entry);
    }
}
