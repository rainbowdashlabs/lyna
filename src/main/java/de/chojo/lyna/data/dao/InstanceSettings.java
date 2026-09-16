package de.chojo.lyna.data.dao;

import java.util.List;

public record InstanceSettings(
        String defaultTheme,
        boolean allowUserTheme,
        List<String> enabledThemes,
        String customThemeColorsJson
) {
}
