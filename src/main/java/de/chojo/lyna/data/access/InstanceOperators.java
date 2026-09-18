/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.InstanceOperator;

import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The people who administer the whole instance, as far as the database is concerned.
 *
 * <p>The other half of the answer lives in `baseSettings.botOwner`, and the two are deliberately
 * different: the configured ids are the root set and cannot be taken away from here, so an operator
 * who removes the wrong row - or a database edited by hand - never locks everybody out. Ids added
 * here can be added and removed freely.
 */
public class InstanceOperators {
    /**
     * @return every operator granted through the web, oldest first
     */
    public List<InstanceOperator> all() {
        return query("""
                SELECT discord_id, added_by, added_at
                FROM instance_operator
                ORDER BY added_at
                """)
                .single()
                .map(row -> new InstanceOperator(
                        row.getLong("discord_id"),
                        row.getObject("added_by") == null ? null : row.getLong("added_by"),
                        row.getTimestamp("added_at").toInstant()))
                .all();
    }

    /**
     * @param discordId the id to check
     * @return whether that id was granted the instance through the web
     */
    public boolean contains(long discordId) {
        return query("SELECT 1 FROM instance_operator WHERE discord_id = ?")
                .single(call().bind(discordId))
                .map(row -> Boolean.TRUE)
                .first()
                .orElse(Boolean.FALSE);
    }

    /**
     * Grants the instance to one more id. Granting it to somebody who already has it changes
     * nothing, so a repeated request is not an error.
     *
     * @param discordId who to grant it to
     * @param addedBy   who granted it, for the record
     * @return whether this added somebody new
     */
    public boolean add(long discordId, Long addedBy) {
        return query("""
                INSERT INTO instance_operator (discord_id, added_by)
                VALUES (?, ?)
                ON CONFLICT (discord_id) DO NOTHING
                """).single(call().bind(discordId).bind(addedBy)).insert().changed();
    }

    /**
     * @return whether that id had been granted the instance here
     */
    public boolean remove(long discordId) {
        return query("DELETE FROM instance_operator WHERE discord_id = ?")
                .single(call().bind(discordId))
                .delete()
                .changed();
    }

    /**
     * @return how many ids hold the instance through the web
     */
    public int count() {
        return query("SELECT count(*) AS total FROM instance_operator")
                .single()
                .map(row -> row.getInt("total"))
                .first()
                .orElse(0);
    }
}
