/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.instance.entity;

import java.time.Instant;

/**
 * Somebody granted the whole instance through the web.
 *
 * @param addedBy who granted it, or nothing when the row predates the record being kept
 */
public record InstanceOperator(long discordId, Long addedBy, Instant addedAt) {}
