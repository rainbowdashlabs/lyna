/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** The chart types the viewer draws; the backend leaves charts of other types out of a report. */
export const CHART_TYPES = ['line', 'area', 'bar', 'stacked-bar', 'scatter', 'pie', 'donut', 'heatmap', 'gauge'] as const

export type ChartType = typeof CHART_TYPES[number]

/**
 * How a plugin describes a chart. Only {@link type} and {@link data} are required; data is loosely
 * typed and read by {@link normalizeChart} according to the type.
 */
export interface ChartSpec {
    type: ChartType
    data: unknown
    unit?: string
    x?: ChartAxis
    y?: ChartAxis
    marks?: ChartMark[]
    [hint: string]: unknown
}

export interface ChartAxis {
    label?: string
    labels?: string[]
    min?: number
    max?: number
}

/** A reference line across the chart, at an x or a y value. */
export interface ChartMark {
    x?: number | string
    y?: number
    label?: string
}

export type ChartValue = number | null

export interface ChartSeries {
    name: string
    /** Values by position, when the series has no x values of its own. */
    values?: ChartValue[]
    /** Points with explicit x values. */
    points?: [number | string, ChartValue][]
}

export interface ChartCategory {
    name: string
    value: ChartValue
}

export interface ChartNeedle {
    name: string
    value: number
    min?: number
    max?: number
}

export type NormalizedChart =
    | {kind: 'series', series: ChartSeries[]}
    | {kind: 'categories', categories: ChartCategory[]}
    | {kind: 'matrix', cells: [number | string, number | string, ChartValue][]}
    | {kind: 'gauge', needles: ChartNeedle[]}
    | {kind: 'raw'}

const RAW: NormalizedChart = {kind: 'raw'}

/**
 * Reads the data of a chart according to its type. Whatever cannot be read as the type expects
 * comes back as `raw`, so the viewer can show the data as it was sent.
 */
export function normalizeChart(spec: ChartSpec): NormalizedChart {
    switch (spec.type) {
        case 'line':
        case 'area':
        case 'stacked-bar':
        case 'scatter':
            return asSeries(spec.data) ?? RAW
        case 'bar':
            return asSeries(spec.data) ?? asCategories(spec.data) ?? RAW
        case 'pie':
        case 'donut':
            return asCategories(spec.data) ?? RAW
        case 'heatmap':
            return asMatrix(spec.data) ?? RAW
        case 'gauge':
            return asGauge(spec.data) ?? RAW
        default:
            return RAW
    }
}

/** A number, a string holding one, or null for a gap; undefined when the value is neither. */
function numberOf(value: unknown): ChartValue | undefined {
    if (value === null) return null
    if (typeof value === 'number') return Number.isFinite(value) ? value : null
    if (typeof value === 'string' && value.trim() !== '') {
        const parsed = Number(value)
        return Number.isFinite(parsed) ? parsed : undefined
    }
    return undefined
}

function isRecord(value: unknown): value is Record<string, unknown> {
    return typeof value === 'object' && value !== null && !Array.isArray(value)
}

function xOf(value: unknown): number | string | undefined {
    const number = numberOf(value)
    if (typeof number === 'number') return number
    return typeof value === 'string' ? value : undefined
}

/** One series from a list of values or a list of points. */
function seriesOf(name: string, data: unknown): ChartSeries | null {
    if (!Array.isArray(data)) return null
    const values = data.map(numberOf)
    if (values.every(value => value !== undefined)) return {name, values: values as ChartValue[]}
    const points: [number | string, ChartValue][] = []
    for (const point of data) {
        const pair = Array.isArray(point) && point.length === 2 ? point : isRecord(point) ? [point.x, point.y] : null
        const x = pair ? xOf(pair[0]) : undefined
        const y = pair ? numberOf(pair[1]) : undefined
        if (x === undefined || y === undefined) return null
        points.push([x, y])
    }
    return {name, points}
}

function asSeries(data: unknown): NormalizedChart | null {
    if (Array.isArray(data)) {
        const series = seriesOf('', data)
        return series ? {kind: 'series', series: [series]} : null
    }
    if (!isRecord(data)) return null
    const series = Object.entries(data).map(([name, values]) => seriesOf(name, values))
    if (series.length === 0 || series.some(entry => entry === null)) return null
    return {kind: 'series', series: series as ChartSeries[]}
}

function asCategories(data: unknown): NormalizedChart | null {
    const pairs = Array.isArray(data)
        ? data.map(entry => isRecord(entry) ? [entry.name, entry.value] : [undefined, undefined])
        : isRecord(data) ? Object.entries(data) : null
    if (!pairs || pairs.length === 0) return null
    const categories: ChartCategory[] = []
    for (const [name, value] of pairs) {
        const number = numberOf(value)
        if (name === undefined || name === null || number === undefined) return null
        categories.push({name: String(name), value: number})
    }
    return {kind: 'categories', categories}
}

/** Rows of a matrix, or cells naming their own x and y. */
function asMatrix(data: unknown): NormalizedChart | null {
    if (!Array.isArray(data) || data.length === 0) return null
    const cells: [number | string, number | string, ChartValue][] = []
    for (const [row, entry] of data.entries()) {
        if (Array.isArray(entry)) {
            for (const [column, value] of entry.entries()) {
                const number = numberOf(value)
                if (number === undefined) return null
                cells.push([column, row, number])
            }
        } else if (isRecord(entry)) {
            const x = xOf(entry.x)
            const y = xOf(entry.y)
            const value = numberOf(entry.value)
            if (x === undefined || y === undefined || value === undefined) return null
            cells.push([x, y, value])
        } else {
            return null
        }
    }
    return {kind: 'matrix', cells}
}

function needleOf(name: string, value: unknown): ChartNeedle | null {
    const number = numberOf(value)
    if (typeof number === 'number') return {name, value: number}
    if (!isRecord(value)) return null
    const reading = numberOf(value.value)
    if (typeof reading !== 'number') return null
    const min = numberOf(value.min)
    const max = numberOf(value.max)
    return {
        name: typeof value.name === 'string' ? value.name : name,
        value: reading,
        ...(typeof min === 'number' ? {min} : {}),
        ...(typeof max === 'number' ? {max} : {}),
    }
}

function asGauge(data: unknown): NormalizedChart | null {
    const single = needleOf('', data)
    if (single) return {kind: 'gauge', needles: [single]}
    const entries = Array.isArray(data)
        ? data.map(entry => needleOf('', entry))
        : isRecord(data) ? Object.entries(data).map(([name, value]) => needleOf(name, value)) : []
    if (entries.length === 0 || entries.some(entry => entry === null)) return null
    return {kind: 'gauge', needles: entries as ChartNeedle[]}
}
