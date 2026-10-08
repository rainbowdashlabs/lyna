/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.product.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.download.entity.ReleaseType;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What a product is, what it grants, and who has tried it.
 *
 * <p>Statements only. Whether somebody may download something is weighed in the service, because the
 * answer depends on roles Discord holds rather than on rows here.
 */
@Singleton
public class ProductRepository {

    public boolean delete(int productId, long guildId) {
        return query("DELETE FROM product WHERE id = ? AND guild_id = ?")
                .single(call().bind(productId).bind(guildId))
                .delete()
                .changed();
    }

    /**
     * @return whether this member has a trial of the product still to spend
     */
    /**
     * @return every guild that has products, for an operator choosing one with no gateway to ask
     */
    public List<Long> guildsWithProducts() {
        return query("SELECT DISTINCT guild_id FROM product ORDER BY guild_id")
                .single()
                .map(row -> row.getLong("guild_id"))
                .all();
    }

    /**
     * Whether a trial of the product is still to be taken by this account, counting one its linked
     * Discord account took on Discord.
     *
     * @param discordId the account's Discord id, or null when it has none linked
     */
    public boolean trialUnspent(int productId, int accountId, Long discordId) {
        return query("""
                SELECT NOT exists(
                    SELECT 1 FROM trial WHERE product_id = ? AND (account_id = ? OR (user_id IS NOT NULL AND user_id = ?))
                ) AS unspent
                """)
                .single(call().bind(productId).bind(accountId).bind(discordId))
                .map(row -> row.getBoolean("unspent"))
                .first()
                .orElse(false);
    }

    /**
     * Spends the account's trial of the product, recording its Discord id too when it has one.
     */
    public void spendTrial(int productId, int accountId, Long discordId) {
        query("INSERT INTO trial(product_id, account_id, user_id) VALUES(?,?,?) ON CONFLICT DO NOTHING")
                .single(call().bind(productId).bind(accountId).bind(discordId))
                .insert();
    }

    /**
     * @return what a licence held by this member opens
     */
    public List<ReleaseType> accessByHolder(int productId, long discordId) {
        return query("SELECT release_type FROM user_product_access WHERE user_id = ? AND product_id = ?")
                .single(call().bind(discordId).bind(productId))
                .map(row -> row.getEnum("release_type", ReleaseType.class))
                .all();
    }

    /**
     * @return what the member's Discord roles open for this product
     */
    public List<ReleaseType> accessByRoles(int productId, List<Long> roleIds) {
        return query("SELECT release_type FROM role_access WHERE product_id = ? AND ARRAY[role_id] && ?")
                .single(call().bind(productId).bind(roleIds, PostgreSqlTypes.BIGINT))
                .map(row -> row.getEnum("release_type", ReleaseType.class))
                .all();
    }

    /**
     * @return the ids of the licences this member holds for the product, in this guild
     */
    public List<Integer> licenseIdsFor(long guildId, int productId, long discordId) {
        return query("""
                SELECT
                	guild_id,
                	user_id,
                	product_id,
                	license_id,
                	user_identifier,
                	key
                FROM
                	user_license_all
                WHERE guild_id = ?
                  AND product_id = ?
                  AND user_id = ?""")
                .single(call().bind(guildId).bind(productId).bind(discordId))
                .map(row -> row.getInt("license_id"))
                .all();
    }

    /**
     * Writes one column of a product.
     *
     * <p>The column is interpolated because a placeholder cannot name one. Every caller passes a
     * literal, and nothing here takes a column name from outside.
     */
    public boolean set(
            int productId,
            String column,
            java.util.function.Function<de.chojo.sadu.queries.api.call.Call, de.chojo.sadu.queries.api.call.Call>
                    value) {
        return query("""
                UPDATE
                    product
                SET %s = ?
                WHERE
                    id = ?""", column)
                .single(value.apply(call()).bind(productId))
                .update()
                .changed();
    }
}
