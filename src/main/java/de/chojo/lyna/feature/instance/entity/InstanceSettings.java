/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.instance.entity;

import java.util.List;

public record InstanceSettings(
        String defaultTheme, boolean allowUserTheme, List<String> enabledThemes, String customThemeColorsJson) {}
