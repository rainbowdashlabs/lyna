/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.manifest.entity;

import java.util.List;

/**
 * A plugin a build needs or can use.
 *
 * @param min the lowest version it works with, inclusive, or null
 * @param max the highest version it works with, inclusive, or null
 */
public record ManifestDependency(String name, boolean required, String min, String max, List<ManifestLink> links) {}
