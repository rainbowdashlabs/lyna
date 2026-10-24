/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.updater.entity;

/**
 * How a product hands out dashboard keys.
 *
 * @param enabled     whether accounts may hold dashboard keys for it at all
 * @param refreshDays how old a key may get before a refresh trades it for its successor
 * @param expiryDays  how long a key may go unused before it expires
 * @param allowance   the keys a buyer may hold before asking for more
 */
public record DashboardSettings(boolean enabled, int refreshDays, int expiryDays, int allowance) {
    /** What a product has until an admin changes it. */
    public static final DashboardSettings DEFAULT = new DashboardSettings(false, 7, 30, 10);

    /** The most days either interval may be set to. */
    public static final int MAX_DAYS = 3650;

    /** The most keys a buyer may be allowed by default. */
    public static final int MAX_ALLOWANCE = 1000;

    /**
     * @return whether every number is within what may be stored
     */
    public boolean valid() {
        return refreshDays >= 1
                && refreshDays <= MAX_DAYS
                && expiryDays >= 1
                && expiryDays <= MAX_DAYS
                && allowance >= 0
                && allowance <= MAX_ALLOWANCE;
    }
}
