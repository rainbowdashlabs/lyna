/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.entity;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.JsonNode;

import java.util.List;

/**
 * A debug report in the second format: the plugin and server metadata as the client describes them, and the sections
 * in the order the client wants them shown. The client decides what a report holds; the server only checks it.
 *
 * @param v        the format, always 2
 * @param plugin   the plugin's metadata, at least its {@code name} and {@code version}
 * @param server   the server's metadata
 * @param sections the sections, in the order they are shown
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DebugSubmissionV2(int v, JsonNode plugin, JsonNode server, List<Section> sections) {

    /**
     * @param kind decides how the viewer shows it, one of {@link DebugEntryKind}
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Section(String kind, String name, String content) {}
}
