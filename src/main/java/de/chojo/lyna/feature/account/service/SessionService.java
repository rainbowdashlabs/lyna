/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.service;

import com.google.inject.Inject;
import com.google.inject.Singleton;
import de.chojo.lyna.auth.JwtService;
import de.chojo.lyna.feature.account.repository.AccountSessionRepository;

import java.util.Optional;

/**
 * Opens, recognises, renews and ends the sessions accounts are signed in with.
 *
 * <p>A token is only ever a ticket for a session row. It is accepted while its signature holds and
 * the row is live; the row is what logging out ends, so a token that has not expired yet is still
 * refused once its session has been.
 *
 * <p>Renewal is a side effect of being recognised: a token older than the renewal age comes back
 * with a successor under the same session id, and the row's expiry moves with it. The predecessor
 * stays valid until its own expiry, so two tabs renewing at once both keep working.
 */
@Singleton
public class SessionService {
    private final JwtService jwt;
    private final AccountSessionRepository sessions;

    @Inject
    public SessionService(JwtService jwt, AccountSessionRepository sessions) {
        this.jwt = jwt;
        this.sessions = sessions;
    }

    /**
     * Signs an account in on a new device.
     *
     * @param userAgent what the device calls itself, shown in the account's session list
     * @return the token to hand to that device
     */
    public JwtService.Issued open(int accountId, String userAgent) {
        JwtService.Issued issued = jwt.issue(accountId);
        sessions.record(issued.jti(), accountId, issued.expiresAt(), userAgent);
        return issued;
    }

    /**
     * @param token what the device presented
     * @return the session it belongs to, when that session is live, and a successor token when this
     *     one is due for renewal
     */
    public Optional<Recognised> recognise(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<JwtService.Verified> verified = jwt.verify(token);
        if (verified.isEmpty() || !sessions.touchIfLive(verified.get().jti())) return Optional.empty();
        if (!jwt.dueForRenewal(verified.get())) return Optional.of(new Recognised(verified.get(), null));
        return renew(verified.get()).map(renewed -> new Recognised(verified.get(), renewed));
    }

    /**
     * Renews a live session whatever the age of its token, for moving a session from one carrier to
     * another without signing it out.
     *
     * @return the successor, or empty when the token belongs to no live session
     */
    public Optional<JwtService.Issued> adopt(String token) {
        if (token == null || token.isBlank()) return Optional.empty();
        Optional<JwtService.Verified> verified = jwt.verify(token);
        if (verified.isEmpty() || !sessions.touchIfLive(verified.get().jti())) return Optional.empty();
        return renew(verified.get());
    }

    /**
     * Ends one session, on whatever device it is.
     *
     * @return whether it was still open
     */
    public boolean end(String jti) {
        return sessions.revoke(jti);
    }

    private Optional<JwtService.Issued> renew(JwtService.Verified session) {
        JwtService.Issued renewed = jwt.renew(session);
        if (!sessions.renew(session.jti(), renewed.expiresAt())) return Optional.empty();
        return Optional.of(renewed);
    }

    /**
     * @param session what the presented token says
     * @param renewed the token to replace it with, or null when it is young enough to keep
     */
    public record Recognised(JwtService.Verified session, JwtService.Issued renewed) {
        public Optional<JwtService.Issued> renewal() {
            return Optional.ofNullable(renewed);
        }
    }
}
