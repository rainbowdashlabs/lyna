package de.chojo.lyna.data.dao;

import java.util.List;

public record InstanceSettings(
        String defaultTheme,
        String defaultFeel,
        boolean lockFeel,
        boolean allowUserTheme,
        boolean allowUserFeel,
        List<String> enabledThemes,
        String customThemeColorsJson
) {
}
