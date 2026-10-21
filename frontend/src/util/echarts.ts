/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {use} from 'echarts/core'
import {CanvasRenderer} from 'echarts/renderers'
import {BarChart, GaugeChart, HeatmapChart, LineChart, PieChart, ScatterChart} from 'echarts/charts'
import {DataZoomComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent, VisualMapComponent} from 'echarts/components'

/**
 * Registers the parts of echarts the debug report charts use, so only those end up in the bundle.
 */
export function registerCharts() {
    use([
        CanvasRenderer,
        BarChart, GaugeChart, HeatmapChart, LineChart, PieChart, ScatterChart,
        DataZoomComponent, GridComponent, LegendComponent, MarkLineComponent, TooltipComponent, VisualMapComponent,
    ])
}
