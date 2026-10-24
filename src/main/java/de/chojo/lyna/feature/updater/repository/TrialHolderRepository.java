/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.repository;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The trials accounts took, which decide who may hold server keys of a product without a licence or a
 * role.
 *
 * <p>A trial counts whether it was taken on the web, by the account, or on Discord, by the Discord
 * account linked to it.
 */
public class TrialHolderRepository {
    private static final String TRIAL_OF_ACCOUNT = """
            (t.account_id = ? OR t.user_id::TEXT IN (
                SELECT external_id FROM account_identity WHERE account_id = ? AND provider = 'discord'))
            """;

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
