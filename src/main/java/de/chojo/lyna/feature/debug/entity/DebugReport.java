/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import com.fasterxml.jackson.annotation.JsonRawValue;

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
     * @param length the characters it holds, so the viewer can say how large a section is before loading it
     */
    public record Section(int position, DebugEntryKind kind, String name, int length, int lines) {}

    /**
     * A section ready to be stored, in the order it is shown.
     */
    public record NewSection(DebugEntryKind kind, String name, String content) {}
}
