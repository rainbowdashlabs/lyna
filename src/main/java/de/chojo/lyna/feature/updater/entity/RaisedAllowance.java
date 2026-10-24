/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

import org.jetbrains.annotations.Nullable;

/**
 * An account allowed more keys of one kind on a product than the default.
 *
 * @param account the username of the account
 * @param granted the keys allowed on top of the default
 * @param keyTypeId   the product key type for server keys, null for upgrade tokens
 * @param keyTypeName its name
 * @param used        the keys of that kind it holds now
 */
public record RaisedAllowance(
        int accountId,
        String account,
        int productId,
        String productName,
        KeyKind kind,
        @Nullable Integer keyTypeId,
        @Nullable String keyTypeName,
        int granted,
        int used) {}
