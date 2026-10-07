/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.repository;

import com.google.inject.Singleton;
import de.chojo.lyna.feature.debug.entity.DebugEntryKind;
import de.chojo.lyna.feature.debug.entity.DebugReport.Section;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

/**
 * Debug reports and their sections.
 *
 * <p>A report is written in one statement with all its sections, so a failure leaves nothing half
 * stored behind. The delete key is stored only as a hash; the read key is what the report is found by
 * and is kept as it is.
 */
@Singleton
public class DebugReportRepository {

    /**
     * A report as stored, without its sections.
     *
     * @param pluginMeta JSON
     * @param serverMeta JSON
     */
    public record Head(
            int id,
            String pluginName,
            String pluginVersion,
            Instant created,
            String pluginMeta,
            String serverMeta) {}

    /**
     * Stores a report.
     *
     * @param sections the sections as a JSON array of {@code {kind, name, content}}, in the order they
     *                 are shown
     */
    public void create(
            String readKey,
            String deleteKeyHash,
            String pluginName,
            String pluginVersion,
            String pluginMeta,
            String serverMeta,
            String sections) {
        query("""
                WITH report AS (
                    INSERT INTO debug_report (read_key, delete_key_hash, plugin_name, plugin_version, plugin_meta, server_meta)
                    VALUES (?, ?, ?, ?, ?::JSONB, ?::JSONB)
                    RETURNING id
                ), sections AS (
                    INSERT INTO debug_report_entry (report_id, position, kind, name, content)
                    SELECT report.id, section.position - 1, section.value ->> 'kind', section.value ->> 'name', section.value ->> 'content'
                    FROM report, jsonb_array_elements(?::JSONB) WITH ORDINALITY AS section(value, position)
                    RETURNING report_id
                )
                SELECT id FROM report
                """)
                .single(call()
                        .bind(readKey)
                        .bind(deleteKeyHash)
                        .bind(pluginName)
                        .bind(pluginVersion)
                        .bind(pluginMeta)
                        .bind(serverMeta)
                        .bind(sections))
                .map(row -> row.getInt("id"))
                .first()
                .orElseThrow();
    }

    public Optional<Head> byReadKey(String readKey) {
        return query("""
                SELECT id, plugin_name, plugin_version, created, plugin_meta::TEXT AS plugin_meta, server_meta::TEXT AS server_meta
                FROM debug_report WHERE read_key = ?
                """)
                .single(call().bind(readKey))
                .map(row -> new Head(
                        row.getInt("id"),
                        row.getString("plugin_name"),
                        row.getString("plugin_version"),
                        row.getTimestamp("created").toInstant(),
                        row.getString("plugin_meta"),
                        row.getString("server_meta")))
                .first();
    }

    public List<Section> sections(int reportId) {
        return query("""
                SELECT position, kind, name, char_length(content) AS length,
                       cardinality(string_to_array(content, E'\\n')) AS lines
                FROM debug_report_entry WHERE report_id = ? ORDER BY position
                """)
                .single(call().bind(reportId))
                .map(row -> new Section(
                        row.getInt("position"),
                        DebugEntryKind.valueOf(row.getString("kind")),
                        row.getString("name"),
                        row.getInt("length"),
                        row.getInt("lines")))
                .all();
    }

    public Optional<String> content(String readKey, int position) {
        return query("""
                SELECT e.content FROM debug_report_entry e JOIN debug_report r ON r.id = e.report_id
                WHERE r.read_key = ? AND e.position = ?
                """)
                .single(call().bind(readKey).bind(position))
                .map(row -> row.getString("content"))
                .first();
    }

    /**
     * @return whether there was a report with this key to delete
     */
    public boolean deleteByDeleteKeyHash(String deleteKeyHash) {
        return query("DELETE FROM debug_report WHERE delete_key_hash = ?")
                .single(call().bind(deleteKeyHash))
                .delete()
                .changed();
    }

    /**
     * @return how many reports were older than that and are gone
     */
    public int deleteOlderThanDays(int days) {
        return query("DELETE FROM debug_report WHERE created < now() - make_interval(days => ?)")
                .single(call().bind(days))
                .delete()
                .rows();
    }
}
