/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.entity;

/**
 * A product as the storefront shows it, across every guild the bot serves.
 *
 * <p>Read straight from the tables rather than through {@link Products}, which resolves a product
 * through its guild and so needs the gateway. The storefront is the one surface that has to answer
 * to a visitor who is not signed in and may reach an instance whose bot is not connected, so it
 * carries the columns it needs and nothing that has to be looked up on Discord.
 *
 * @param free        whether anyone may download it, which decides the button the tile offers
 * @param purchaseUrl where to buy it, when a Ko-fi code has been mapped to it
 */
public record KioskProduct(
        int id, long guildId, String name, String url, String iconUrl, boolean free, String purchaseUrl) {}
