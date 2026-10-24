/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.builtbybit.entity;

import org.jetbrains.annotations.Nullable;

import java.time.Instant;

/**
 * One call BuiltByBit made, as the purchase log shows it.
 *
 * @param productId   the product it was matched to, if any
 * @param productName that product's name, if any
 * @param raw         the body as received
 */
public record BuiltByBitLogEntry(
        int id,
        Instant received,
        BuiltByBitCall call,
        @Nullable Long memberId,
        @Nullable Long resourceId,
        @Nullable Long addonId,
        @Nullable Integer productId,
        @Nullable String productName,
        String outcome,
        String raw) {}
