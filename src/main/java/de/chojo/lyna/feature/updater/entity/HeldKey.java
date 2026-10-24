/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * A key as a guild's admin sees it: whose it is and how it is used, never the key itself.
 *
 * @param account the username of the account holding it
 */
public record HeldKey(
        int id,
        int accountId,
        String account,
        int productId,
        String productName,
        KeyKind kind,
        String name,
        Instant createdAt,
        @Nullable Instant lastUsedAt,
        @Nullable Instant expiresAt) {}
