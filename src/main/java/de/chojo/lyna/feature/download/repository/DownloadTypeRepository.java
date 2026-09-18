/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.download.repository;

import com.google.inject.Singleton;
import de.chojo.sadu.queries.api.call.Call;

import java.util.function.Function;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * The kinds of release a guild offers - stable, dev, snapshot and whatever else it names.
 */
@Singleton
public class DownloadTypeRepository {

    /**
     * Writes one column of a download type.
     *
     * <p>The column is interpolated because a placeholder cannot name one. Every caller passes a
     * literal, and nothing here takes a column name from outside.
     */
    public boolean set(int typeId, String column, Function<Call, Call> value) {
        return query("UPDATE download_type SET %s = ? WHERE id = ?", column)
                .single(value.apply(call()).bind(typeId))
                .update()
                .changed();
    }

    public boolean delete(long guildId, int typeId) {
        return query("DELETE FROM download_type WHERE guild_id = ? AND id =  ?")
                .single(call().bind(guildId).bind(typeId))
                .delete()
                .changed();
    }
}
