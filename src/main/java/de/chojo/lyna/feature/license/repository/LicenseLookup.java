/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.repository;

import de.chojo.lyna.feature.guild.Guilds;
import de.chojo.lyna.feature.license.entity.License;

import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Finds a license by its key without being told the guild, for the account area, where somebody
 * redeeming a key has no guild in front of them.
 */
public class LicenseLookup {
    private final Guilds guilds;

    public LicenseLookup(Guilds guilds) {
        this.guilds = guilds;
    }

    public Optional<License> byKey(String key) {
        return query("SELECT guild_id FROM guild_license WHERE key = ?")
                .single(call().bind(key))
                .map(row -> row.getLong("guild_id"))
                .first()
                .flatMap(guildId -> guilds.guild(guildId).licenses().byKey(key));
    }
}
