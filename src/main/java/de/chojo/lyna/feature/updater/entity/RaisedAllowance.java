/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

/**
 * An account allowed more keys of one kind on a product than the default.
 *
 * @param account the username of the account
 * @param granted the keys allowed on top of the default
 * @param used    the keys of that kind it holds now
 */
public record RaisedAllowance(
        int accountId, String account, int productId, String productName, KeyKind kind, int granted, int used) {}
