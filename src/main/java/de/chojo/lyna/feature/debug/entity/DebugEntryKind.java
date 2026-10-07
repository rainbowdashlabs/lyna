/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.entity;

/**
 * What a section of a debug report holds, which decides how the viewer shows it.
 */
public enum DebugEntryKind {
    /**
     * A block of metadata a plugin adds about itself.
     */
    META,
    /**
     * The server's latest.log.
     */
    LOG,
    /**
     * The lines of the log the plugin wrote itself.
     */
    PLUGIN_LOG,
    /**
     * An exception or warning thrown by the plugin.
     */
    INTERNAL_EXCEPTION,
    /**
     * An exception or warning thrown by something else on the server.
     */
    EXTERNAL_EXCEPTION,
    /**
     * One of the plugin's configuration files.
     */
    CONFIG
}
