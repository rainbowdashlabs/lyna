/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.instance.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import de.chojo.lyna.feature.instance.entity.InstanceSettings;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Pattern;

/**
 * What an operator may set about how the application looks, and which theme an account may pick.
 *
 * <p>The backend stores theme names and nothing else about them, so it keeps a copy of the ids in the
 * frontend's catalogue ({@code frontend/src/theme/themes.ts}). Without it a typo in the enabled list
 * would be stored, and the frontend would then offer nobody anything. A test holds the two lists in
 * step.
 *
 * <p>Custom colours are a partial theme in the catalogue's own shape: the page tokens
 * ({@code bgLight}, {@code bgLightAccent}, {@code bgDark}, {@code bgDarkAccent}) at the top, and the
 * mode tokens under {@code light} and {@code dark}. Each value is a hex colour. Hex is a narrower
 * language than CSS accepts, on purpose: the values are written into a style block in the first
 * response, where anything wider is a way to inject rules, and the frontend reads them as hex to work
 * out which text colour stays legible on them.
 */
public class AppearancePolicy {
    /** The theme ids the frontend ships, in its order. */
    public static final List<String> THEMES = List.of("transistor", "lyna", "midnight", "forest");

    /** Tokens that hold for the whole page, whichever mode it is in. */
    public static final Set<String> PAGE_TOKENS = Set.of("bgLight", "bgLightAccent", "bgDark", "bgDarkAccent");

    /** The two modes, each of which takes the {@link #MODE_TOKENS}. */
    public static final Set<String> MODES = Set.of("light", "dark");

    /** Tokens a mode defines. */
    public static final Set<String> MODE_TOKENS = Set.of(
            "primary", "primaryAccent", "secondary", "secondaryAccent", "info", "infoAccent", "success", "error");

    private static final Pattern HEX_COLOR = Pattern.compile("#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})");

    private final ObjectMapper json = new ObjectMapper();

    /**
     * Checks what an operator submitted and puts it in the form that is stored: theme ids trimmed and
     * without duplicates, blank custom colours as none, and custom colours compacted.
     *
     * @param settings what the operator submitted
     * @return the settings to store
     * @throws IllegalArgumentException naming what is wrong, in words fit to show the operator
     */
    public InstanceSettings validated(InstanceSettings settings) {
        if (settings == null) throw new IllegalArgumentException("Nothing was submitted.");
        String defaultTheme =
                settings.defaultTheme() == null ? "" : settings.defaultTheme().trim();
        if (!THEMES.contains(defaultTheme)) {
            throw new IllegalArgumentException("Unknown default theme \"%s\". Known themes: %s."
                    .formatted(defaultTheme, String.join(", ", THEMES)));
        }
        List<String> enabled = enabledThemes(settings.enabledThemes());
        if (!enabled.isEmpty() && !enabled.contains(defaultTheme)) {
            throw new IllegalArgumentException(
                    "The default theme \"%s\" has to be one of the enabled themes.".formatted(defaultTheme));
        }
        return new InstanceSettings(
                defaultTheme, settings.allowUserTheme(), enabled, customColors(settings.customThemeColorsJson()));
    }

    /**
     * Whether an account may pick a theme for itself under this policy.
     *
     * @param policy what the operator set
     * @param theme  the theme the account asks for
     * @return true when the operator lets accounts choose and this theme is one they may choose
     */
    public boolean offers(InstanceSettings policy, String theme) {
        if (!policy.allowUserTheme() || !THEMES.contains(theme)) return false;
        return policy.enabledThemes().isEmpty() || policy.enabledThemes().contains(theme);
    }

    private static List<String> enabledThemes(List<String> submitted) {
        if (submitted == null) return List.of();
        Set<String> enabled = new LinkedHashSet<>();
        List<String> unknown = new ArrayList<>();
        for (String entry : submitted) {
            String id = entry == null ? "" : entry.trim();
            if (id.isEmpty()) continue;
            if (THEMES.contains(id)) enabled.add(id);
            else unknown.add(id);
        }
        if (!unknown.isEmpty()) {
            throw new IllegalArgumentException("Unknown theme %s: %s. Known themes: %s."
                    .formatted(
                            unknown.size() == 1 ? "id" : "ids", String.join(", ", unknown), String.join(", ", THEMES)));
        }
        return List.copyOf(enabled);
    }

    private String customColors(String submitted) {
        if (submitted == null || submitted.isBlank()) return null;
        JsonNode root;
        try {
            root = json.readTree(submitted);
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("Custom theme colors are not valid JSON.");
        }
        if (root == null || !root.isObject()) {
            throw new IllegalArgumentException("Custom theme colors have to be a JSON object.");
        }
        for (Map.Entry<String, JsonNode> entry : root.properties()) {
            String key = entry.getKey();
            if (MODES.contains(key)) checkMode(key, entry.getValue());
            else if (PAGE_TOKENS.contains(key)) checkColor(key, entry.getValue());
            else throw new IllegalArgumentException("Unknown theme token \"%s\".".formatted(key));
        }
        return root.toString();
    }

    private static void checkMode(String mode, JsonNode tokens) {
        if (!tokens.isObject()) {
            throw new IllegalArgumentException("\"%s\" has to be an object of colors.".formatted(mode));
        }
        for (Map.Entry<String, JsonNode> entry : tokens.properties()) {
            String path = mode + "." + entry.getKey();
            if (!MODE_TOKENS.contains(entry.getKey())) {
                throw new IllegalArgumentException("Unknown theme token \"%s\".".formatted(path));
            }
            checkColor(path, entry.getValue());
        }
    }

    private static void checkColor(String token, JsonNode value) {
        if (value.isTextual() && HEX_COLOR.matcher(value.asText()).matches()) return;
        throw new IllegalArgumentException(
                "\"%s\" is not a color. Use hex notation, like #e92063 or #e26.".formatted(token));
    }
}
