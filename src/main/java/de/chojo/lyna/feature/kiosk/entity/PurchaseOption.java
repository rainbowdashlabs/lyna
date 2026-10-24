/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.kiosk.entity;

/**
 * One place a product can be bought.
 *
 * @param platform where
 * @param url      the page that sells it there
 */
public record PurchaseOption(Platform platform, String url) {
    /**
     * The platforms Lyna takes purchases from, in the order a buyer is offered them.
     */
    public enum Platform {
        KOFI,
        BUILTBYBIT
    }
}
