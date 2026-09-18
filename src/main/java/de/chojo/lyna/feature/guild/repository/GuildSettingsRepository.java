/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.guild.repository;

import com.google.inject.Singleton;
import de.chojo.sadu.queries.api.call.Call;

import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * What a guild has decided for itself: how many shares a licence carries, who administers it, and how
 * long a trial lasts.
 *
 * <p>Each write is an upsert, because a guild that has never changed a setting has no row and should
 * not need one made first.
 */
@Singleton
public class GuildSettingsRepository {

    public boolean setShares(long guildId, int shares) {
        return query("""
                INSERT INTO license_settings(guild_id, shares) VALUES(?,?)
                ON CONFLICT(guild_id)
                     DO UPDATE
                         SET shares = excluded.shares
                """).single(call().bind(guildId).bind(shares)).insert().changed();
    }

    public boolean setAdminRole(long guildId, Long adminRoleId) {
        return query("""
                INSERT INTO license_settings(guild_id, admin_role_id) VALUES(?,?)
                ON CONFLICT(guild_id)
                     DO UPDATE
                         SET admin_role_id = excluded.admin_role_id
                """)
                .single(call().bind(guildId).bind(adminRoleId))
                .insert()
                .changed();
    }

    /**
     * Writes one column of a guild's trial settings.
     *
     * <p>The column is interpolated because a placeholder cannot name one. Every caller passes a
     * literal, and nothing here takes a column name from outside.
     */
    public boolean setTrial(String column, Function<Call, Call> value) {
        return query("""
                INSERT
                INTO
                	trial_settings(guild_id, %s)
                VALUES
                	(?, ?)
                ON CONFLICT(guild_id) DO UPDATE SET
                	%s = ?""", column).single(value.apply(call())).insert().changed();
    }
}
