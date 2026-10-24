/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * One server an account runs a product on, holding a key of one {@link KeyKind}, under the name its
 * owner gave it.
 *
 * @param token       the key that is valid now; every trade or rotation replaces it
 * @param lastUsedAt  when the key was last used: an upgrade token traded for a download, a dashboard key
 *                    refreshed or verified by the gateway; null if it never was
 * @param lastReuseAt when a key this entry had already given up was presented again, which means
 *                    somebody else holds a copy; null if that never happened
 * @param rotatedAt   when the current key was issued; null for an upgrade token from before this was
 *                    recorded
 * @param refreshedAt when the server holding a dashboard key last asked to refresh it; null if it never
 *                    did
 * @param refreshAt   when a refresh will trade a dashboard key for its successor; null for an upgrade
 *                    token
 * @param expiresAt   when a dashboard key expires unless it is used before; null for an upgrade token
 */
public record UpdateToken(
        int id,
        int accountId,
        int productId,
        String productName,
        KeyKind kind,
        String name,
        String token,
        Instant createdAt,
        @Nullable Instant lastUsedAt,
        @Nullable Instant lastReuseAt,
        @Nullable Instant rotatedAt,
        @Nullable Instant refreshedAt,
        @Nullable Instant refreshAt,
        @Nullable Instant expiresAt) {}
