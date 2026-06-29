package de.chojo.lyna.data.access;

import java.sql.Timestamp;
import java.time.Instant;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class RevokedJtis {

    public void revoke(String jti, Instant expiresAt) {
        query("""
                INSERT INTO revoked_jti (jti, expires_at)
                VALUES (?, ?)
                ON CONFLICT (jti) DO NOTHING
                """)
                .single(call().bind(jti).bind(Timestamp.from(expiresAt)))
                .insert();
    }

    public boolean isRevoked(String jti) {
        return query("SELECT 1 FROM revoked_jti WHERE jti = ?")
                .single(call().bind(jti))
                .map(row -> Boolean.TRUE)
                .first()
                .orElse(Boolean.FALSE);
    }

    public int prune() {
        return query("DELETE FROM revoked_jti WHERE expires_at < now()")
                .single(call())
                .delete()
                .rows();
    }
}
