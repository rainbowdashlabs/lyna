/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

/**
 * An account allowed more upgrade tokens on a product than the default.
 *
 * @param account the username of the account
 * @param granted the tokens allowed on top of the default
 * @param used    the tokens it holds now
 */
public record RaisedAllowance(
        int accountId, String account, int productId, String productName, int granted, int used) {}
