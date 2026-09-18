/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.account.entity;

import java.util.List;

/**
 * A license as the account area shows it: across every guild the holder has one in, and without the
 * gateway, so it reads the same whether or not the bot is connected.
 *
 * @param role          whether the account owns this license or was shared it
 * @param ownerAccountId the account the license belongs to, which a sharee sees as a username
 * @param shareesUsed   how many people the owner has shared it with
 * @param shareesCap    how many the guild allows, which is what the owner is measured against
 */
public record AccountLicense(
        int id,
        long guildId,
        int productId,
        String productName,
        String productUrl,
        String userIdentifier,
        List<String> releaseTypes,
        Role role,
        int ownerAccountId,
        int shareesUsed,
        int shareesCap) {
    public enum Role {
        OWNER,
        SHAREE
    }
}
