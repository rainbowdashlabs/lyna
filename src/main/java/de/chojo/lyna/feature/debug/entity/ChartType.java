/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
package de.chojo.lyna.feature.debug.entity;

import java.util.Arrays;
import java.util.Optional;

/**
 * The chart types the viewer draws. A chart of another type is left out of the report.
 */
public enum ChartType {
    LINE("line"),
    AREA("area"),
    BAR("bar"),
    STACKED_BAR("stacked-bar"),
    SCATTER("scatter"),
    PIE("pie"),
    DONUT("donut"),
    HEATMAP("heatmap"),
    GAUGE("gauge");

    private final String wireName;

    ChartType(String wireName) {
        this.wireName = wireName;
    }

    /**
     * @return the type a plugin sends under this name, if the viewer draws it
     */
    public static Optional<ChartType> of(String wireName) {
        return Arrays.stream(values())
                .filter(type -> type.wireName.equals(wireName))
                .findFirst();
    }
}
