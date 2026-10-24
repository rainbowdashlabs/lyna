/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import java.time.Instant;

/**
 * A key an entry has given up, by a trade, a rotation or a reissue.
 *
 * @param entryId   the entry it belonged to, which holds its successor
 * @param retiredAt when it was given up
 */
public record RetiredKey(int entryId, KeyKind kind, Instant retiredAt) {}
