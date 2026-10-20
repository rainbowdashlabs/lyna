/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * An account asking for more upgrade tokens on a product than it is allowed.
 *
 * @param amount  how many more it asks for
 * @param account the username of the account, for whoever decides
 */
public record UpdateTokenRequest(
        int id,
        int accountId,
        String account,
        int productId,
        String productName,
        int amount,
        String reason,
        Status status,
        Instant createdAt,
        @Nullable Instant decidedAt) {
    public enum Status {
        PENDING,
        APPROVED,
        DECLINED
    }
}
