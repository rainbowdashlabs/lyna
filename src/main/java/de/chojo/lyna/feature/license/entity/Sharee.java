/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.entity;

/**
 * Somebody a licence is shared with.
 *
 * @param discordId the sharee's Discord id, or null for somebody who holds this through the web
 *                  alone and can therefore be given no Discord role
 * @param name      what to call them
 */
public record Sharee(Long discordId, String name) {
    public String display() {
        return discordId == null ? name : "<@%d> (%s)".formatted(discordId, name);
    }
}
