/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.access;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class PasswordResetTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    public Issued issue(int accountId, Instant expiresAt) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : buf) sb.append(String.format("%02x", b));
        String token = sb.toString();
        String hash = hash(token);

        query("DELETE FROM password_reset_token WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete();
        query("""
                INSERT INTO password_reset_token (token_hash, account_id, expires_at)
                VALUES (?, ?, ?)
                """)
                .single(call().bind(hash).bind(accountId).bind(Timestamp.from(expiresAt)))
                .insert();
        return new Issued(token, expiresAt);
    }

    public Optional<Integer> consume(String token) {
        String hash = hash(token);
        Optional<Integer> accountId = query("""
                SELECT account_id FROM password_reset_token
                WHERE token_hash = ? AND expires_at > now()
                """)
                .single(call().bind(hash))
                .map(row -> row.getInt("account_id"))
                .first();
        if (accountId.isPresent()) {
            query("DELETE FROM password_reset_token WHERE token_hash = ?")
                    .single(call().bind(hash))
                    .delete();
        }
        return accountId;
    }

    public int prune() {
        return query("DELETE FROM password_reset_token WHERE expires_at < now()")
                .single(call())
                .delete()
                .rows();
    }

    private static String hash(String token) {
        return Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
    }

    public record Issued(String token, Instant expiresAt) {}
}
