package de.chojo.lyna.data.access;

import com.google.common.hash.Hashing;

import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The tokens that prove somebody can read the address they gave.
 *
 * <p>The address is carried on the token rather than read from the account, because the same token
 * confirms a new address as confirms the first one: until it is followed, the account keeps the
 * address it had. That is what stops somebody typing a colleague's address into the change form and
 * quietly taking their mail.
 *
 * <p>Only the hash is stored. A token is in a mailbox and in a link, and neither is somewhere it
 * should also be readable from the database.
 */
public class EmailVerificationTokens {
    private static final SecureRandom RANDOM = new SecureRandom();

    /**
     * Issues a token for an address, replacing any the account already had outstanding.
     *
     * @param accountId whose address it is
     * @param email     the address being confirmed
     * @param expiresAt when the link stops working
     * @return the token to put in the link, which is not stored anywhere in this form
     */
    public Issued issue(int accountId, String email, Instant expiresAt) {
        byte[] buf = new byte[32];
        RANDOM.nextBytes(buf);
        StringBuilder sb = new StringBuilder(64);
        for (byte b : buf) sb.append(String.format("%02x", b));
        String token = sb.toString();

        query("DELETE FROM email_verification_token WHERE account_id = ?")
                .single(call().bind(accountId))
                .delete();
        query("""
                INSERT INTO email_verification_token (token_hash, account_id, email, expires_at)
                VALUES (?, ?, ?, ?)
                """)
                .single(call().bind(hash(token)).bind(accountId).bind(email).bind(Timestamp.from(expiresAt)))
                .insert();
        return new Issued(token, email, expiresAt);
    }

    /**
     * Spends a token.
     *
     * @param token the token from the link
     * @return whose address it confirms and which address, or nothing when the token is unknown or
     * has expired
     */
    public Optional<Confirmed> consume(String token) {
        String hash = hash(token);
        Optional<Confirmed> confirmed = query("""
                SELECT account_id, email FROM email_verification_token
                WHERE token_hash = ? AND expires_at > now()
                """)
                .single(call().bind(hash))
                .map(row -> new Confirmed(row.getInt("account_id"), row.getString("email")))
                .first();
        if (confirmed.isPresent()) {
            query("DELETE FROM email_verification_token WHERE token_hash = ?")
                    .single(call().bind(hash))
                    .delete();
        }
        return confirmed;
    }

    /**
     * @param accountId whose outstanding token to read
     * @return the address a token is outstanding for, when there is one
     */
    public Optional<String> pendingEmail(int accountId) {
        return query("""
                SELECT email FROM email_verification_token
                WHERE account_id = ? AND expires_at > now()
                """)
                .single(call().bind(accountId))
                .map(row -> row.getString("email"))
                .first();
    }

    /**
     * @return how many tokens were past their expiry and are now gone
     */
    public int prune() {
        return query("DELETE FROM email_verification_token WHERE expires_at < now()")
                .single(call())
                .delete()
                .rows();
    }

    private static String hash(String token) {
        return Hashing.sha256().hashString(token, StandardCharsets.UTF_8).toString();
    }

    public record Issued(String token, String email, Instant expiresAt) {
    }

    public record Confirmed(int accountId, String email) {
    }
}
