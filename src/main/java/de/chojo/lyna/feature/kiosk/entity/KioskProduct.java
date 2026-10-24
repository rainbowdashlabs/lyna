/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.entity;

import java.util.List;

/**
 * A product as the storefront shows it, across every guild the bot serves.
 *
 * <p>Read straight from the tables rather than through {@link de.chojo.lyna.feature.product.repository.Products}, which resolves a product
 * through its guild and so needs the gateway. The storefront is the one surface that has to answer
 * to a visitor who is not signed in and may reach an instance whose bot is not connected, so it
 * carries the columns it needs and nothing that has to be looked up on Discord.
 *
 * @param free        whether anyone may download it, which decides the button the tile offers
 * @param purchaseOptions where to buy it, one per platform it is sold on, in the order they are offered
 * @param description what the product says about itself, as markdown, or nothing when unwritten
 * @param trial       whether somebody without a license may download it once to try it
 * @param pageReadme  whether its page shows the project's GitHub README instead of the description
 * @param wikiUrl     its documentation, or null when it has none
 * @param parentId    the product it is an add-on of, or null when it stands on its own
 */
public record KioskProduct(
        int id,
        long guildId,
        String name,
        String url,
        String iconUrl,
        boolean free,
        List<PurchaseOption> purchaseOptions,
        String description,
        boolean trial,
        boolean pageReadme,
        String wikiUrl,
        Integer parentId) {}
