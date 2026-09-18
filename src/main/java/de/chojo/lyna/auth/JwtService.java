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

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public class JwtService {
    private static final String ISSUER = "lyna";

    private final Algorithm algorithm;
    private final JWTVerifier verifier;
    private final long expirySeconds;

    public JwtService(Auth config) {
        if (config.jwtSecret() == null || config.jwtSecret().isBlank()) {
            throw new IllegalStateException("auth.jwtSecret is not configured");
        }
        this.algorithm = Algorithm.HMAC256(config.jwtSecret());
        this.verifier = JWT.require(algorithm).withIssuer(ISSUER).build();
        this.expirySeconds = config.jwtExpirySeconds();
    }

    public Issued issue(int accountId, Long discordId) {
        String jti = UUID.randomUUID().toString();
        Instant now = Instant.now();
        Instant expiresAt = now.plusSeconds(expirySeconds);
        var builder = JWT.create()
                .withIssuer(ISSUER)
                .withSubject(Integer.toString(accountId))
                .withJWTId(jti)
                .withIssuedAt(now)
                .withExpiresAt(expiresAt);
        if (discordId != null) {
            builder.withClaim("discord_id", discordId);
        }
        return new Issued(builder.sign(algorithm), jti, expiresAt);
    }

    public Optional<Verified> verify(String token) {
        try {
            DecodedJWT decoded = verifier.verify(token);
            int accountId = Integer.parseInt(decoded.getSubject());
            Long discordId = decoded.getClaim("discord_id").isNull()
                    ? null
                    : decoded.getClaim("discord_id").asLong();
            return Optional.of(new Verified(accountId, discordId, decoded.getId(), decoded.getExpiresAtAsInstant()));
        } catch (JWTVerificationException | NumberFormatException e) {
            return Optional.empty();
        }
    }

    public record Issued(String token, String jti, Instant expiresAt) {}

    public record Verified(int accountId, Long discordId, String jti, Instant expiresAt) {}
}
