/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

import de.chojo.lyna.feature.updater.entity.ProductKeyType;
import de.chojo.sadu.mapper.wrapper.Row;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.sql.SQLException;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The kinds of server key products hand out.
 *
 * <p>Writing is limited to a product of the guild doing the writing, so an admin of one guild cannot
 * reach another guild's key types by guessing an id.
 */
public class ProductKeyTypeRepository {
    private static final String SELECT = """
            SELECT k.id, k.product_id, k.slug, k.name, k.command, k.enabled, k.refresh_days, k.expiry_days,
                   k.allowance, k.trial_allowance, k.gateway_secret
            FROM product_key_type k
            """;

    public Optional<ProductKeyType> byId(int id) {
        return query(SELECT + "WHERE k.id = ?")
                .single(call().bind(id))
                .map(ProductKeyTypeRepository::read)
                .first();
    }

    /**
     * @return the key type the secret belongs to
     */
    public Optional<ProductKeyType> bySecret(String gatewaySecret) {
        return query(SELECT + "WHERE k.gateway_secret = ?")
                .single(call().bind(gatewaySecret))
                .map(ProductKeyTypeRepository::read)
                .first();
    }

    /**
     * @return the product's key types, by name
     */
    public List<ProductKeyType> forProduct(int productId) {
        return query(SELECT + "WHERE k.product_id = ? ORDER BY k.name, k.id")
                .single(call().bind(productId))
                .map(ProductKeyTypeRepository::read)
                .all();
    }

    /**
     * @return the enabled key types of the products, by product and name
     */
    public List<ProductKeyType> enabledFor(List<Integer> productIds) {
        if (productIds.isEmpty()) return List.of();
        return query(SELECT + "WHERE k.enabled AND k.product_id = ANY(?) ORDER BY k.product_id, k.name, k.id")
                .single(call().bind(productIds, PostgreSqlTypes.INTEGER))
                .map(ProductKeyTypeRepository::read)
                .all();
    }

    /**
     * @return the new key type, or empty when the product does not belong to the guild or already has a
     *     type by that slug
     */
    public Optional<ProductKeyType> create(long guildId, int productId, ProductKeyType.Draft draft, String secret) {
        Optional<Integer> id = query("""
                INSERT INTO product_key_type
                    (product_id, slug, name, command, enabled, refresh_days, expiry_days, allowance, trial_allowance,
                     gateway_secret)
                SELECT id, ?, ?, ?, ?, ?, ?, ?, ?, ? FROM product WHERE id = ? AND guild_id = ?
                ON CONFLICT (product_id, slug) DO NOTHING
                RETURNING id
                """)
                .single(call().bind(draft.slug())
                        .bind(draft.name())
                        .bind(draft.command())
                        .bind(draft.enabled())
                        .bind(draft.refreshDays())
                        .bind(draft.expiryDays())
                        .bind(draft.allowance())
                        .bind(draft.trialAllowance())
                        .bind(secret)
                        .bind(productId)
                        .bind(guildId))
                .map(row -> row.getInt("id"))
                .first();
        return id.flatMap(this::byId);
    }

    /**
     * Changes a key type. Keys already held stay; they are judged by the new settings from their next
     * use on.
     *
     * @return the changed key type, or empty when there is no such type on a product of the guild or its
     *     new slug is taken
     */
    public Optional<ProductKeyType> update(long guildId, int id, ProductKeyType.Draft draft) {
        boolean changed = query("""
                UPDATE product_key_type k
                SET slug = ?, name = ?, command = ?, enabled = ?, refresh_days = ?, expiry_days = ?, allowance = ?,
                    trial_allowance = ?
                FROM product p
                WHERE k.id = ? AND p.id = k.product_id AND p.guild_id = ?
                  AND NOT EXISTS (SELECT 1 FROM product_key_type o
                                  WHERE o.product_id = k.product_id AND o.slug = ? AND o.id <> k.id)
                """)
                .single(call().bind(draft.slug())
                        .bind(draft.name())
                        .bind(draft.command())
                        .bind(draft.enabled())
                        .bind(draft.refreshDays())
                        .bind(draft.expiryDays())
                        .bind(draft.allowance())
                        .bind(draft.trialAllowance())
                        .bind(id)
                        .bind(guildId)
                        .bind(draft.slug()))
                .update()
                .changed();
        return changed ? byId(id) : Optional.empty();
    }

    /**
     * Gives a key type a new gateway secret; the old one stops working at once.
     *
     * @return the key type with its new secret, or empty when there is no such type on a product of the
     *     guild
     */
    public Optional<ProductKeyType> replaceSecret(long guildId, int id, String secret) {
        boolean changed = query("""
                UPDATE product_key_type k SET gateway_secret = ?
                FROM product p
                WHERE k.id = ? AND p.id = k.product_id AND p.guild_id = ?
                """)
                .single(call().bind(secret).bind(id).bind(guildId))
                .update()
                .changed();
        return changed ? byId(id) : Optional.empty();
    }

    /**
     * Ends a key type and every key of it.
     *
     * @return whether there was such a type on a product of the guild
     */
    public boolean delete(long guildId, int id) {
        return query(
                        "DELETE FROM product_key_type k USING product p WHERE k.id = ? AND p.id = k.product_id AND p.guild_id = ?")
                .single(call().bind(id).bind(guildId))
                .delete()
                .changed();
    }

    private static ProductKeyType read(Row row) throws SQLException {
        return new ProductKeyType(
                row.getInt("id"),
                row.getInt("product_id"),
                row.getString("slug"),
                row.getString("name"),
                row.getString("command"),
                row.getBoolean("enabled"),
                row.getInt("refresh_days"),
                row.getInt("expiry_days"),
                row.getInt("allowance"),
                row.getInt("trial_allowance"),
                row.getString("gateway_secret"));
    }
}
