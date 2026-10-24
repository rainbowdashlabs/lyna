/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.entity;

/**
 * A purchase as the BuiltByBit webhook reports it.
 *
 * @param memberId the buyer's BuiltByBit member id
 * @param username the buyer's BuiltByBit name
 * @param listing  what was bought
 * @param bundle   whether it was bought as part of a bundle
 */
public record BuiltByBitPurchase(long memberId, String username, BuiltByBitListing listing, boolean bundle) {}
