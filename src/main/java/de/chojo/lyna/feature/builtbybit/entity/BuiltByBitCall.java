/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.entity;

/**
 * Which of the two calls BuiltByBit made.
 */
public enum BuiltByBitCall {
    /** The purchase webhook. */
    PURCHASE,
    /** The licence key placeholder, asked on every download. */
    KEY
}
