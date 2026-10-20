/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.repository;

import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.util.Collection;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The roles an account wears, kept so access by role is answered from the database rather than by
 * asking Discord.
 *
 * <p>Rows mirrored from Discord carry the source {@code DISCORD}, and only those are ever replaced or
 * cleared by a sync. A row from any other source belongs to whoever wrote it.
 */
public class AccountRoleRepository {
    private static final String DISCORD = "DISCORD";

    /**
     * Makes the account's Discord roles in one guild exactly these, in one statement so a reader never
     * sees the account with none.
     */
    public void replaceDiscordRoles(int accountId, long guildId, Collection<Long> roleIds) {
        List<Long> roles = List.copyOf(roleIds);
        query("""
                WITH gone AS (
                    DELETE FROM account_role
                    WHERE account_id = ? AND guild_id = ? AND source = ? AND role_id <> ALL(?)
                )
                INSERT INTO account_role (account_id, guild_id, role_id, source)
                SELECT ?, ?, role_id, ? FROM unnest(?) AS role_id
                ON CONFLICT (account_id, role_id, source) DO UPDATE SET guild_id = excluded.guild_id, synced_at = now()
                """)
                .single(call().bind(accountId)
                        .bind(guildId)
                        .bind(DISCORD)
                        .bind(roles, PostgreSqlTypes.BIGINT)
                        .bind(accountId)
                        .bind(guildId)
                        .bind(DISCORD)
                        .bind(roles, PostgreSqlTypes.BIGINT))
                .insert();
    }

    /**
     * Forgets the account's Discord roles in one guild, for somebody who left it.
     */
    public void clearDiscordRoles(int accountId, long guildId) {
        query("DELETE FROM account_role WHERE account_id = ? AND guild_id = ? AND source = ?")
                .single(call().bind(accountId).bind(guildId).bind(DISCORD))
                .delete();
    }

    /**
     * Forgets every Discord role of the account, for one that no longer has Discord linked.
     */
    public void clearDiscordRoles(int accountId) {
        query("DELETE FROM account_role WHERE account_id = ? AND source = ?")
                .single(call().bind(accountId).bind(DISCORD))
                .delete();
    }

    /**
     * Forgets a role that no longer exists, whoever gave it.
     */
    public void removeRole(long roleId) {
        query("DELETE FROM account_role WHERE role_id = ?")
                .single(call().bind(roleId))
                .delete();
    }

    /**
     * @return every role the account wears, from any source
     */
    public List<Long> roles(int accountId) {
        return query("SELECT DISTINCT role_id FROM account_role WHERE account_id = ? ORDER BY role_id")
                .single(call().bind(accountId))
                .map(row -> row.getLong("role_id"))
                .all();
    }
}
