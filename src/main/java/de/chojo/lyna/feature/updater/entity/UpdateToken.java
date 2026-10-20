/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * One updater an account runs for a product, under the name its owner gave it.
 *
 * @param token       the upgrade token that is valid now; every trade replaces it
 * @param lastUsedAt  when the updater last traded its token for a download, null if it never has
 * @param lastReuseAt when a token this entry had already traded in was presented again, which means
 *                    somebody else holds a copy; null if that never happened
 */
public record UpdateToken(
        int id,
        int accountId,
        int productId,
        String productName,
        String name,
        String token,
        Instant createdAt,
        @Nullable Instant lastUsedAt,
        @Nullable Instant lastReuseAt) {}
