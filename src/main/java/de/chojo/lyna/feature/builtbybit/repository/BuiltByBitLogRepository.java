/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitCall;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitLogEntry;
import de.chojo.lyna.feature.builtbybit.entity.BuiltByBitOutcome;
import de.chojo.sadu.mapper.wrapper.Row;
import org.jetbrains.annotations.Nullable;

import java.sql.SQLException;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Every call BuiltByBit made and what came of it.
 *
 * <p>Neither the purchase webhook nor the key placeholder is signed, so this is how an operator finds
 * out what was asked of Lyna in BuiltByBit's name. Only calls carrying the right secret are kept;
 * anybody could otherwise fill the table.
 */
@Singleton
public class BuiltByBitLogRepository {

    /**
     * What one call carried, as far as it could be read.
     *
     * @param productId the product it was matched to, if any
     * @param raw       the body as received
     */
    public record Received(
            BuiltByBitCall call,
            @Nullable Long memberId,
            @Nullable Long resourceId,
            @Nullable Long addonId,
            @Nullable Integer productId,
            String raw) {}

    public void record(Received received, BuiltByBitOutcome outcome) {
        query("""
                INSERT INTO builtbybit_purchase_log (kind, member_id, resource_id, addon_id, product_id, outcome, raw)
                VALUES (?, ?, ?, ?, ?, ?, ?)
                """)
                .single(call().bind(received.call())
                        .bind(received.memberId())
                        .bind(received.resourceId())
                        .bind(received.addonId())
                        .bind(received.productId())
                        .bind(outcome)
                        .bind(received.raw()))
                .insert();
    }

    /**
     * The newest calls concerning a guild: those matched to one of its products, and those matched to
     * none, since an unknown resource is what an operator most needs to see.
     */
    public List<BuiltByBitLogEntry> forGuild(long guildId, int limit) {
        return query("""
                SELECT l.id, l.received, l.kind, l.member_id, l.resource_id, l.addon_id, l.product_id,
                       p.name AS product_name, l.outcome, l.raw
                FROM builtbybit_purchase_log l
                LEFT JOIN product p ON p.id = l.product_id
                WHERE l.product_id IS NULL OR p.guild_id = ?
                ORDER BY l.received DESC, l.id DESC
                LIMIT ?
                """)
                .single(call().bind(guildId).bind(limit))
                .map(this::entry)
                .all();
    }

    private BuiltByBitLogEntry entry(Row row) throws SQLException {
        return new BuiltByBitLogEntry(
                row.getInt("id"),
                row.getTimestamp("received").toInstant(),
                BuiltByBitCall.valueOf(row.getString("kind")),
                (Long) row.getObject("member_id"),
                (Long) row.getObject("resource_id"),
                (Long) row.getObject("addon_id"),
                (Integer) row.getObject("product_id"),
                row.getString("product_name"),
                row.getString("outcome"),
                row.getString("raw"));
    }
}
