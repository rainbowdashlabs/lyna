/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {EChartsOption, SeriesOption} from 'echarts'
import type {ChartCategory, ChartSeries, ChartSpec, NormalizedChart} from './chartData'

/** Series longer than this get a zoom bar. */
const ZOOM_FROM = 200

/**
 * Builds the echarts option for a chart whose data was read by `normalizeChart`. Returns null for
 * `raw` data, which the viewer shows as it was sent.
 */
export function chartOption(spec: ChartSpec, chart: NormalizedChart): EChartsOption | null {
    const base: EChartsOption = {
        backgroundColor: 'transparent',
        tooltip: {valueFormatter: value => withUnit(value, spec.unit)},
    }
    switch (chart.kind) {
        case 'series':
            return {...base, ...seriesOption(spec, chart.series)}
        case 'categories':
            return {...base, ...categoryOption(spec, chart.categories)}
        case 'matrix':
            return {...base, ...matrixOption(spec, chart.cells)}
        case 'gauge':
            return {
                ...base,
                series: [{
                    type: 'gauge',
                    min: chart.needles[0]?.min ?? spec.y?.min ?? 0,
                    max: chart.needles[0]?.max ?? spec.y?.max ?? Math.max(100, ...chart.needles.map(needle => needle.value)),
                    detail: {formatter: value => withUnit(value, spec.unit)},
                    data: chart.needles.map(needle => ({name: needle.name, value: needle.value})),
                }],
            }
        default:
            return null
    }
}

function withUnit(value: unknown, unit?: string): string {
    if (value === null || value === undefined) return '–'
    return unit ? `${String(value)} ${unit}` : String(value)
}

function seriesOption(spec: ChartSpec, series: ChartSeries[]): EChartsOption {
    const explicit = series.some(entry => entry.points)
    const textX = series.some(entry => entry.points?.some(([x]) => typeof x === 'string'))
    const length = Math.max(...series.map(entry => entry.values?.length ?? entry.points?.length ?? 0))
    const labels = spec.x?.labels ?? Array.from({length}, (_, index) => String(index))
    return {
        legend: series.length > 1 ? {top: 0} : undefined,
        grid: {left: 48, right: 24, top: series.length > 1 ? 36 : 16, bottom: length > ZOOM_FROM ? 64 : 32, containLabel: true},
        xAxis: explicit
            ? {type: textX ? 'category' : 'value', name: spec.x?.label}
            : {type: 'category', name: spec.x?.label, data: labels},
        yAxis: {type: 'value', name: spec.y?.label, min: spec.y?.min, max: spec.y?.max},
        tooltip: {trigger: spec.type === 'scatter' ? 'item' : 'axis', valueFormatter: value => withUnit(value, spec.unit)},
        dataZoom: length > ZOOM_FROM ? [{type: 'inside'}, {type: 'slider'}] : undefined,
        series: series.map((entry, index) => ({
            ...seriesStyle(spec.type),
            name: entry.name,
            data: entry.points ?? entry.values ?? [],
            ...(index === 0 ? marks(spec) : {}),
        }) as SeriesOption),
    }
}

function seriesStyle(type: string): SeriesOption {
    switch (type) {
        case 'area':
            return {type: 'line', areaStyle: {}, showSymbol: false}
        case 'bar':
            return {type: 'bar'}
        case 'stacked-bar':
            return {type: 'bar', stack: 'total'}
        case 'scatter':
            return {type: 'scatter'}
        default:
            return {type: 'line', showSymbol: false}
    }
}

function marks(spec: ChartSpec): SeriesOption {
    if (!spec.marks?.length) return {}
    return {
        markLine: {
            symbol: 'none',
            data: spec.marks.map(mark => mark.y !== undefined
                ? {yAxis: mark.y, name: mark.label, label: {formatter: mark.label ?? ''}}
                : {xAxis: mark.x, name: mark.label, label: {formatter: mark.label ?? ''}}),
        },
    } as SeriesOption
}

function categoryOption(spec: ChartSpec, categories: ChartCategory[]): EChartsOption {
    if (spec.type === 'pie' || spec.type === 'donut') {
        return {
            legend: {type: 'scroll', bottom: 0},
            tooltip: {trigger: 'item', valueFormatter: value => withUnit(value, spec.unit)},
            series: [{
                type: 'pie',
                radius: spec.type === 'donut' ? ['45%', '70%'] : '70%',
                data: categories.map(category => ({name: category.name, value: category.value ?? 0})),
            }],
        }
    }
    return {
        grid: {left: 48, right: 24, top: 16, bottom: 32, containLabel: true},
        xAxis: {type: 'category', name: spec.x?.label, data: categories.map(category => category.name)},
        yAxis: {type: 'value', name: spec.y?.label, min: spec.y?.min, max: spec.y?.max},
        tooltip: {trigger: 'axis', valueFormatter: value => withUnit(value, spec.unit)},
        series: [{type: 'bar', data: categories.map(category => category.value), ...marks(spec)} as SeriesOption],
    }
}

/** Cells may name x and y by label or by position; both become positions on labelled axes. */
function matrixOption(spec: ChartSpec, cells: [number | string, number | string, number | null][]): EChartsOption {
    const xs = axisLabels(cells.map(cell => cell[0]), spec.x?.labels)
    const ys = axisLabels(cells.map(cell => cell[1]), spec.y?.labels)
    const values = cells.map(cell => cell[2]).filter((value): value is number => value !== null)
    return {
        grid: {left: 48, right: 24, top: 16, bottom: 72, containLabel: true},
        xAxis: {type: 'category', name: spec.x?.label, data: xs.labels},
        yAxis: {type: 'category', name: spec.y?.label, data: ys.labels},
        tooltip: {trigger: 'item'},
        visualMap: {
            min: Math.min(0, ...values),
            max: Math.max(1, ...values),
            calculable: true,
            orient: 'horizontal',
            left: 'center',
            bottom: 0,
        },
        series: [{type: 'heatmap', data: cells.map(([x, y, value]) => [xs.index(x), ys.index(y), value])}],
    }
}

function axisLabels(keys: (number | string)[], given?: string[]) {
    const named = [...new Set(keys.filter((key): key is string => typeof key === 'string'))]
    const count = Math.max(0, ...keys.filter((key): key is number => typeof key === 'number').map(key => key + 1))
    const labels = given ?? [...Array.from({length: count}, (_, index) => String(index)), ...named]
    return {
        labels,
        index: (key: number | string) => typeof key === 'number' ? key : Math.max(0, labels.indexOf(key)),
    }
}
