/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.dao.settings;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class License {
    private final Settings settings;
    int shares = 0;
    Long adminRoleId;

    public License(Settings settings) {
        this.settings = settings;
    }

    public License(Settings settings, int shares, Long adminRoleId) {
        this.settings = settings;
        this.shares = shares;
        this.adminRoleId = adminRoleId;
    }

    public int shares() {
        return shares;
    }

    public void shares(int shares) {
        if (query("""
                INSERT INTO license_settings(guild_id, shares) VALUES(?,?)
                ON CONFLICT(guild_id)
                     DO UPDATE
                         SET shares = excluded.shares
                """).single(call().bind(guildId()).bind(shares)).insert().changed()) {
            this.shares = shares;
        }
    }

    /**
     * The Discord role whose members administer this guild here, beyond those who already may by
     * holding MANAGE_SERVER.
     *
     * <p>How a guild delegates its admin area without handing out a server-wide permission.
     *
     * @return the role, or nothing when the guild has named none
     */
    public Long adminRoleId() {
        return adminRoleId;
    }

    public void adminRoleId(Long adminRoleId) {
        if (query("""
                INSERT INTO license_settings(guild_id, admin_role_id) VALUES(?,?)
                ON CONFLICT(guild_id)
                     DO UPDATE
                         SET admin_role_id = excluded.admin_role_id
                """).single(call().bind(guildId()).bind(adminRoleId)).insert().changed()) {
            this.adminRoleId = adminRoleId;
        }
    }

    public long guildId() {
        return settings.guildId();
    }
}
