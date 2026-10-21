/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonRawValue;
import com.fasterxml.jackson.annotation.JsonUnwrapped;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;

/**
 * A stored debug report without the contents of its sections, which are fetched one at a time.
 *
 * @param created    when it was uploaded, as ISO-8601 like everything the viewer formats
 * @param pluginMeta the plugin's metadata as JSON, as it was uploaded
 * @param serverMeta the server's metadata as JSON, as it was uploaded
 */
public record DebugReport(
        String pluginName,
        String pluginVersion,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant created,
        @JsonFormat(shape = JsonFormat.Shape.STRING) Instant expires,
        @JsonRawValue String pluginMeta,
        @JsonRawValue String serverMeta,
        List<Section> sections) {

    /**
     * @param length  the characters it holds, so the viewer can say how large a section is before loading it
     * @param display how to show it, flattened into the section
     */
    public record Section(
            int position,
            DebugEntryKind kind,
            String name,
            int length,
            int lines,
            @JsonUnwrapped Display display) {}

    /**
     * A section ready to be stored, in the order it is shown.
     */
    public record NewSection(DebugEntryKind kind, String name, String content, Display display) {
        public NewSection(DebugEntryKind kind, String name, String content) {
            this(kind, name, content, Display.NONE);
        }
    }

    /**
     * How a plugin asks for a section to be shown; every part may be missing.
     *
     * @param format      how the content is highlighted, one of {@link #FORMATS}; guessed from the name when null
     * @param level       the outcome of a {@link DebugEntryKind#CHECK}, one of {@link #LEVELS}
     * @param entries     the key and value pairs of a {@link DebugEntryKind#META} section, shown as a table
     * @param occurrences how often an exception occurred in the log
     * @param firstSeen   the log time of its first occurrence
     * @param lastSeen    the log time of its last occurrence
     * @param chart       the type, data and hints of a {@link DebugEntryKind#CHART}, as sent
     */
    public record Display(
            String format,
            String level,
            List<Entry> entries,
            Integer occurrences,
            String firstSeen,
            String lastSeen,
            JsonNode chart) {
        public static final Display NONE = new Display(null, null, null, null, null, null, null);
        public static final List<String> FORMATS = List.of("yaml", "json", "properties", "toml", "log", "text");
        public static final List<String> LEVELS = List.of("OK", "WARN", "ERROR");
    }

    public record Entry(String key, String value) {}
}
