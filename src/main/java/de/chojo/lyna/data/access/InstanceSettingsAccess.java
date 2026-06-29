package de.chojo.lyna.data.access;

import de.chojo.lyna.data.dao.InstanceSettings;
import de.chojo.sadu.postgresql.types.PostgreSqlTypes;

import java.sql.Array;
import java.sql.SQLException;
import java.util.List;

import static de.chojo.sadu.queries.api.call.Call.call;
import static de.chojo.sadu.queries.api.query.Query.query;

public class InstanceSettingsAccess {

    public InstanceSettings get() {
        return query("""
                SELECT default_theme, default_feel, lock_feel,
                       allow_user_theme, allow_user_feel, enabled_themes, custom_theme_colors
                FROM instance_settings WHERE id = 1
                """)
                .single(call())
                .map(row -> new InstanceSettings(
                        row.getString("default_theme"),
                        row.getString("default_feel"),
                        row.getBoolean("lock_feel"),
                        row.getBoolean("allow_user_theme"),
                        row.getBoolean("allow_user_feel"),
                        readStringArray(row.getArray("enabled_themes")),
                        row.getString("custom_theme_colors")))
                .first()
                .orElse(defaults());
    }

    public void update(InstanceSettings next) {
        query("""
                UPDATE instance_settings
                SET default_theme = ?,
                    default_feel = ?,
                    lock_feel = ?,
                    allow_user_theme = ?,
                    allow_user_feel = ?,
                    enabled_themes = ?,
                    custom_theme_colors = ?::JSONB
                WHERE id = 1
                """)
                .single(call()
                        .bind(next.defaultTheme())
                        .bind(next.defaultFeel())
                        .bind(next.lockFeel())
                        .bind(next.allowUserTheme())
                        .bind(next.allowUserFeel())
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
        return new InstanceSettings("lyna", "ROUNDED", false, true, true, List.of(), null);
    }
}
