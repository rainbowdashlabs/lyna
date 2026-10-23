/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.service;

import de.chojo.lyna.feature.instance.entity.InstanceSettings;
import de.chojo.lyna.feature.instance.service.AppearancePolicy;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * What an operator may store about the look of the instance, and which themes an account is offered.
 */
class AppearancePolicyTest {
    private final AppearancePolicy policy = new AppearancePolicy();

    private static InstanceSettings settings(String defaultTheme, List<String> enabled, String colors) {
        return new InstanceSettings(defaultTheme, true, enabled, colors);
    }

    private String refusal(InstanceSettings settings) {
        return assertThrows(IllegalArgumentException.class, () -> policy.validated(settings))
                .getMessage();
    }

    @Test
    @DisplayName("The backend knows exactly the themes the frontend ships")
    void catalogueMatchesTheFrontend() throws IOException {
        String source = Files.readString(Path.of("frontend/src/theme/themes.ts"));
        Matcher matcher = Pattern.compile("(?m)^    (\\w+): \\{$").matcher(source);
        List<String> ids = new ArrayList<>();
        while (matcher.find()) ids.add(matcher.group(1));

        assertEquals(AppearancePolicy.THEMES, ids);
    }

    @Test
    @DisplayName("The mode and page tokens are the ones a frontend theme defines")
    void tokensMatchTheFrontend() throws IOException {
        String source = Files.readString(Path.of("frontend/src/theme/themes.ts"));

        assertEquals(AppearancePolicy.MODE_TOKENS, interfaceFields(source, "ModeColors"));
        List<String> themeFields = new ArrayList<>(interfaceFields(source, "ThemeColors"));
        themeFields.removeAll(AppearancePolicy.MODES);
        assertEquals(AppearancePolicy.PAGE_TOKENS, Set.copyOf(themeFields));
    }

    private static Set<String> interfaceFields(String source, String name) {
        Matcher block = Pattern.compile("interface " + name + " \\{([^}]*)}").matcher(source);
        assertTrue(block.find(), name);
        return Arrays.stream(block.group(1).split("\n"))
                .map(String::trim)
                .filter(line -> line.contains(":"))
                .map(line -> line.substring(0, line.indexOf(':')))
                .collect(Collectors.toSet());
    }

    @Test
    @DisplayName("Valid settings are stored tidied: ids trimmed and deduplicated, colours compacted")
    void validSettingsAreTidied() {
        InstanceSettings stored = policy.validated(settings(
                " midnight ",
                List.of(" lyna", "midnight", "lyna", " "),
                "{ \"bgDark\": \"#000\", \"dark\": { \"primary\": \"#E92063\" } }"));

        assertEquals("midnight", stored.defaultTheme());
        assertTrue(stored.allowUserTheme());
        assertEquals(List.of("lyna", "midnight"), stored.enabledThemes());
        assertEquals("{\"bgDark\":\"#000\",\"dark\":{\"primary\":\"#E92063\"}}", stored.customThemeColorsJson());
    }

    @Test
    @DisplayName("No enabled list and no colours mean every theme and none")
    void absentListsAreEmpty() {
        InstanceSettings stored = policy.validated(settings("lyna", null, "  "));

        assertEquals(List.of(), stored.enabledThemes());
        assertNull(stored.customThemeColorsJson());
    }

    @Test
    @DisplayName("Nothing submitted is refused")
    void nothingSubmitted() {
        assertThrows(IllegalArgumentException.class, () -> policy.validated(null));
    }

    @Test
    @DisplayName("A default theme that does not exist is refused")
    void unknownDefault() {
        assertTrue(refusal(settings("workbench", List.of(), null)).contains("workbench"));
        assertTrue(refusal(settings(null, List.of(), null)).contains("Unknown default theme"));
    }

    @Test
    @DisplayName("An enabled theme that does not exist is refused, naming every one")
    void unknownEnabled() {
        assertTrue(refusal(settings("lyna", List.of("lyna", "nope"), null)).contains("id: nope"));
        assertTrue(refusal(settings("lyna", Arrays.asList("lyna", null, "a", "b"), null))
                .contains("ids: a, b"));
    }

    @Test
    @DisplayName("A default theme outside the enabled list is refused")
    void defaultOutsideEnabled() {
        assertTrue(refusal(settings("forest", List.of("lyna"), null)).contains("enabled"));
    }

    @Test
    @DisplayName("Custom colours that are not a JSON object are refused")
    void colorsNotAnObject() {
        assertTrue(refusal(settings("lyna", List.of(), "{nope")).contains("not valid JSON"));
        assertTrue(refusal(settings("lyna", List.of(), "[\"#fff\"]")).contains("JSON object"));
    }

    @Test
    @DisplayName("A token the theme does not have is refused, at the top and in a mode")
    void unknownTokens() {
        assertTrue(refusal(settings("lyna", List.of(), "{\"accent\":\"#fff\"}")).contains("\"accent\""));
        assertTrue(refusal(settings("lyna", List.of(), "{\"light\":{\"bgLight\":\"#fff\"}}"))
                .contains("\"light.bgLight\""));
    }

    @Test
    @DisplayName("A mode that is not an object of colours is refused")
    void modeNotAnObject() {
        assertTrue(refusal(settings("lyna", List.of(), "{\"dark\":\"#000\"}")).contains("object of colors"));
    }

    @Test
    @DisplayName("A value that is not a hex colour is refused")
    void invalidColors() {
        assertTrue(refusal(settings("lyna", List.of(), "{\"bgLight\":\"red\"}")).contains("\"bgLight\""));
        assertTrue(
                refusal(settings("lyna", List.of(), "{\"bgLight\":\"#12345\"}")).contains("not a color"));
        assertTrue(refusal(settings("lyna", List.of(), "{\"bgLight\":12}")).contains("not a color"));
        assertTrue(refusal(settings("lyna", List.of(), "{\"dark\":{\"error\":\"#fff;}body{x:y\"}}"))
                .contains("\"dark.error\""));
    }

    @Test
    @DisplayName("An account is offered real themes the operator enabled, and nothing while the theme is fixed")
    void offers() {
        InstanceSettings open = new InstanceSettings("lyna", true, List.of(), null);
        InstanceSettings narrowed = new InstanceSettings("lyna", true, List.of("lyna", "forest"), null);
        InstanceSettings fixed = new InstanceSettings("lyna", false, List.of(), null);

        assertTrue(policy.offers(open, "midnight"));
        assertFalse(policy.offers(open, "workbench"));
        assertTrue(policy.offers(narrowed, "forest"));
        assertFalse(policy.offers(narrowed, "midnight"));
        assertFalse(policy.offers(fixed, "lyna"));
    }
}
