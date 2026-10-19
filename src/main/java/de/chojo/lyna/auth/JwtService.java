/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.auth;

import com.auth0.jwt.JWT;
import com.auth0.jwt.JWTVerifier;
import com.auth0.jwt.algorithms.Algorithm;
import com.auth0.jwt.exceptions.JWTVerificationException;
import com.auth0.jwt.interfaces.DecodedJWT;
import de.chojo.lyna.configuration.elements.Auth;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * Signs and reads the tokens a session is carried in.
 *
 * <p>The {@code jti} claim is the session's id. A renewed token keeps it, so the session row and
 * everything that refers to it stay the same while the token in the browser is replaced.
 *
 * <p>A token names the account and nothing about it. A session renewed for months would otherwise
 * carry a Discord id long after that Discord account was unlinked, and anything asking the token
 * rather than the account would believe it.
 */
public class JwtService {
    private static final String ISSUER = "lyna";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final Duration sessionLength;
    private final Duration renewAfter;

    public JwtService(Auth config) {
        if (config.jwtSecret() == null || config.jwtSecret().isBlank()) {
            throw new IllegalStateException("auth.jwtSecret is not configured");
        }
        this.algorithm = Algorithm.HMAC256(config.jwtSecret());
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.sessionLength = Duration.ofDays(config.sessionDays());
        this.renewAfter = Duration.ofHours(config.renewAfterHours());
    }

    /**
     * @return a token for a new session, under an id nothing else has
     */
    public Issued issue(int accountId) {
        return sign(UUID.randomUUID().toString(), accountId);
    }

    /**
     * @return a fresh token for the same session, valid for a whole session length from now
     */
    public Issued renew(Verified session) {
        return sign(session.jti(), session.accountId());
    }

    /**
     * @return whether the token is old enough to be replaced by a renewed one
     */
    public boolean dueForRenewal(Verified session) {
        return session.issuedAt() == null || session.issuedAt().plus(renewAfter).isBefore(Instant.now());
    }

    public Optional<Verified> verify(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);
            int accountId = Integer.parseInt(decoded.getSubject());
            return Optional.of(new Verified(
                    accountId, decoded.getId(), decoded.getIssuedAtAsInstant(), decoded.getExpiresAtAsInstant()));
        } catch (JWTVerificationException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    private Issued sign(String jti, int accountId) {
        Instant now = Instant.now();
        Instant expiresAt = now.plus(sessionLength);
        String token = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(Integer.toString(accountId))
                .withJWTId(jti)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt)
                .sign(algorithm);
        return new Issued(token, jti, expiresAt);
    }

    public record Issued(String token, String jti, Instant expiresAt) {}

    public record Verified(int accountId, String jti, Instant issuedAt, Instant expiresAt) {}
}
