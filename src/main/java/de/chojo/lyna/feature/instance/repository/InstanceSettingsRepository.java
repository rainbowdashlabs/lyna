/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.instance.repository;

import de.chojo.lyna.feature.instance.entity.InstanceSettings;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class InstanceSettingsRepository {

    public InstanceSettings get() {
        return query("""
                SELECT default_theme, allow_user_theme, enabled_themes, custom_theme_colors
                FROM instance_settings WHERE id = 1
                """)
                .single(call())
                .map(row -> new InstanceSettings(
                        row.getString("default_theme"),
                        row.getBoolean("allow_user_theme"),
                        readStringArray(row.getArray("enabled_themes")),
                        row.getString("custom_theme_colors")))
                .first()
                .orElse(defaults());
    }

    public void update(InstanceSettings next) {
        query("""
                UPDATE instance_settings
                SET default_theme = ?,
                    allow_user_theme = ?,
                    enabled_themes = ?,
                    custom_theme_colors = ?::JSONB
                WHERE id = 1
                """)
                .single(call().bind(next.defaultTheme())
                        .bind(next.allowUserTheme())
                        .bind(next.enabledThemes(), PostgreSqlTypes.TEXT)
                        .bind(next.customThemeColorsJson()))
                .update();
    }

    private static List<String> readStringArray(Array array) throws SQLException {
        if (array == null) return List.of();
        Object raw = array.getArray();
        if (raw instanceof String[] strings) return List.of(strings);
        if (raw instanceof Object[] objects) {
            List<String> out = new java.util.ArrayList<>(objects.length);
            for (Object o : objects) out.add(o == null ? null : o.toString());
            return out;
        }
        return List.of();
    }

    private static InstanceSettings defaults() {
        return new InstanceSettings("transistor", true, List.of(), null);
    }
}
