/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.releasepost.repository;

import com.google.inject.Singleton;
import de.chojo.sadu.mapper.wrapper.Row;

import java.sql.SQLException;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The webhook GitHub posts a product's releases to.
 *
 * <p>The secret is kept as it is, unlike a password: checking a signature means computing it again.
 */
@Singleton
public class ReleaseWebhookRepository {

    /**
     * @param token     what the webhook address ends in
     * @param secret    what GitHub signs its deliveries with
     * @param channelId where releases are announced, or 0 for nowhere yet
     */
    public record ReleaseWebhook(int productId, String token, String secret, long channelId) {}

    public Optional<ReleaseWebhook> byToken(String token) {
        return query("SELECT product_id, token, secret, channel_id FROM release_webhook WHERE token = ?")
                .single(call().bind(token))
                .map(ReleaseWebhookRepository::webhook)
                .first();
    }

    public Optional<ReleaseWebhook> ofProduct(int productId) {
        return query("SELECT product_id, token, secret, channel_id FROM release_webhook WHERE product_id = ?")
                .single(call().bind(productId))
                .map(ReleaseWebhookRepository::webhook)
                .first();
    }

    /**
     * Gives a product a webhook, or a new token and secret for the one it has. The channel is kept.
     */
    public void issue(int productId, String token, String secret) {
        query("""
                INSERT INTO release_webhook (product_id, token, secret) VALUES (?, ?, ?)
                ON CONFLICT (product_id) DO UPDATE SET token = excluded.token, secret = excluded.secret
                """).single(call().bind(productId).bind(token).bind(secret)).insert();
    }

    /**
     * @return whether the product has a webhook to set the channel of
     */
    public boolean channel(int productId, long channelId) {
        return query("UPDATE release_webhook SET channel_id = ? WHERE product_id = ?")
                .single(call().bind(channelId == 0 ? null : channelId).bind(productId))
                .update()
                .changed();
    }

    public boolean remove(int productId) {
        return query("DELETE FROM release_webhook WHERE product_id = ?")
                .single(call().bind(productId))
                .delete()
                .changed();
    }

    private static ReleaseWebhook webhook(Row row) throws SQLException {
        return new ReleaseWebhook(
                row.getInt("product_id"), row.getString("token"), row.getString("secret"), row.getLong("channel_id"));
    }
}
