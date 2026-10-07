/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.configuration.elements;

import dev.chojo.ocular.override.Env;
import dev.chojo.ocular.override.Overwrite;
import dev.chojo.ocular.override.OverwritePrefix;
import dev.chojo.ocular.override.Prop;

/**
 * How debug reports uploaded by plugins are kept.
 */
@SuppressWarnings({"FieldMayBeFinal", "CanBeFinal"})
@OverwritePrefix("DEBUGREPORTS")
public class DebugReports {
    /**
     * Days a report is kept before it is deleted. The client tells whoever uploads one that it is gone
     * after fourteen.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private int retentionDays = 14;

    /**
     * The largest upload accepted, in bytes. A server log is most of a report.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private int maxUploadBytes = 16 * 1024 * 1024;

    /**
     * Seconds an address waits between two uploads.
     */
    @Overwrite(env = @Env, prop = @Prop)
    private int uploadIntervalSeconds = 10;

    public int retentionDays() {
        return retentionDays;
    }

    public int maxUploadBytes() {
        return maxUploadBytes;
    }

    public int uploadIntervalSeconds() {
        return uploadIntervalSeconds;
    }
}
