/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.entity;

import java.time.Instant;

public record AccountSession(
        String jti, int accountId, Instant issuedAt, Instant expiresAt, Instant lastSeenAt, String userAgent) {}
