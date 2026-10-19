/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.entity;

import java.time.Instant;

/**
 * One signed-in device.
 *
 * <p>The {@code jti} is the session's id and outlives every token issued for it: renewing re-signs a
 * token under the same id and moves {@code expiresAt}. Ending the session sets {@code revokedAt},
 * which ends every token it ever had.
 */
public record AccountSession(
        String jti,
        int accountId,
        Instant issuedAt,
        Instant expiresAt,
        Instant lastSeenAt,
        String userAgent,
        Instant revokedAt) {}
