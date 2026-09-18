/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.license.entity;

/**
 * Where a licence came from.
 *
 * <p>Its own column rather than a prefix on the identifier: the identifier says who bought it, and
 * making it also say where from meant every reader had to know the prefix, and anything matching on
 * the identifier had to reconstruct it.
 */
public enum LicenseSource {
    /** The shop webhook. */
    KOFI,
    /** A payment receipt parsed out of the mailbox. */
    MAIL,
    /** An operator, by command or through the admin area. */
    MANUAL
}
