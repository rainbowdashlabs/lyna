/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.entity;

import java.time.Instant;

/**
 * The grant Discord gave an account, as stored: both tokens still encrypted.
 */
public record DiscordToken(
        int accountId, byte[] accessToken, byte[] refreshToken, String scope, Instant expiresAt, Instant refreshedAt) {}
