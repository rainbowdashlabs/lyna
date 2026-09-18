/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.web.api.theme;

import com.google.inject.Inject;
import de.chojo.lyna.data.access.InstanceSettingsAccess;
import de.chojo.lyna.data.dao.InstanceSettings;
import io.javalin.http.Context;

import static io.javalin.apibuilder.ApiBuilder.get;
import static io.javalin.apibuilder.ApiBuilder.path;

/**
 * What the operator has decided about how the application looks, for anyone who asks.
 *
 * <p>Anonymous, because the first paint happens before anybody has signed in: the page needs the
 * default theme to render in it rather than in a neutral one it then replaces. Nothing here names a
 * person, so there is nothing to withhold.
 */
public class Theme {
    private final InstanceSettingsAccess instanceSettings;

    @Inject
    public Theme(InstanceSettingsAccess instanceSettings) {
        this.instanceSettings = instanceSettings;
    }

    public void init() {
        path("theme", () -> get("public", this::publicTheme));
    }

    private void publicTheme(Context ctx) {
        InstanceSettings settings = instanceSettings.get();
        ctx.header("Cache-Control", "public, max-age=60");
        ctx.json(new PublicTheme(
                settings.defaultTheme(),
                settings.allowUserTheme(),
                settings.enabledThemes(),
                settings.customThemeColorsJson()));
    }

    public record PublicTheme(
            String defaultTheme,
            boolean allowUserTheme,
            java.util.List<String> enabledThemes,
            String customThemeColors) {}
}
