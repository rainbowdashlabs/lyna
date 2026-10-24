/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

import de.chojo.lyna.feature.updater.entity.DashboardSettings;

import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What decides who may hold dashboard keys for a product, beyond a licence or a role: the product's own
 * settings, and the trials accounts took.
 *
 * <p>A trial counts whether it was taken on the web, by the account, or on Discord, by the Discord
 * account linked to it.
 */
public class DashboardProductRepository {
    private static final String TRIAL_OF_ACCOUNT = """
            (t.account_id = ? OR t.user_id::TEXT IN (
                SELECT external_id FROM account_identity WHERE account_id = ? AND provider = 'discord'))
            """;

    /**
     * @return the product's settings, or empty when there is no such product
     */
    public Optional<DashboardSettings> settings(int productId) {
        return query("""
                SELECT dashboard_keys, dashboard_refresh_days, dashboard_expiry_days, dashboard_allowance
                FROM product WHERE id = ?
                """)
                .single(call().bind(productId))
                .map(row -> new DashboardSettings(
                        row.getBoolean("dashboard_keys"),
                        row.getInt("dashboard_refresh_days"),
                        row.getInt("dashboard_expiry_days"),
                        row.getInt("dashboard_allowance")))
                .first();
    }

    /**
     * Changes how a product of the guild hands out dashboard keys. Keys already held stay; they are
     * judged by the new settings from their next use on.
     *
     * @return whether the product belongs to the guild
     */
    public boolean set(long guildId, int productId, DashboardSettings settings) {
        return query("""
                UPDATE product
                SET dashboard_keys = ?, dashboard_refresh_days = ?, dashboard_expiry_days = ?, dashboard_allowance = ?
                WHERE id = ? AND guild_id = ?
                """)
                .single(call().bind(settings.enabled())
                        .bind(settings.refreshDays())
                        .bind(settings.expiryDays())
                        .bind(settings.allowance())
                        .bind(productId)
                        .bind(guildId))
                .update()
                .changed();
    }

    /**
     * @return whether the account took the product's trial
     */
    public boolean tookTrial(int accountId, int productId) {
        return query("SELECT exists(SELECT 1 FROM trial t WHERE t.product_id = ? AND " + TRIAL_OF_ACCOUNT
                        + ") AS taken")
                .single(call().bind(productId).bind(accountId).bind(accountId))
                .map(row -> row.getBoolean("taken"))
                .first()
                .orElse(false);
    }

    /**
     * @return the products whose trial the account took, in no particular order
     */
    public List<Integer> trialProductIds(int accountId) {
        return query("SELECT DISTINCT t.product_id FROM trial t WHERE " + TRIAL_OF_ACCOUNT)
                .single(call().bind(accountId).bind(accountId))
                .map(row -> row.getInt("product_id"))
                .all();
    }
}
