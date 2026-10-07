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
 * A debug report as eldo-util's {@code DebugUtil} uploads it.
 *
 * <p>The plugin and server metadata are kept as they came, since the viewer shows them as they are and
 * older clients send fewer fields. Anything a client leaves out is null.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record DebugSubmission(
        JsonNode pluginMeta,
        JsonNode serverMeta,
        List<Entry> additionalPluginMeta,
        LogData latestLog,
        List<Entry> configDumps) {

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record Entry(String name, String content) {}

    @JsonIgnoreProperties(ignoreUnknown = true)
    public record LogData(
            String log, String pluginLog, List<String> internalExceptions, List<String> externalExceptions) {}
}
