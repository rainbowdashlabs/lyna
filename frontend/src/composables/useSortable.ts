/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, ref, toValue, type ComputedRef, type MaybeRefOrGetter, type Ref} from 'vue'

export type SortDirection = 'asc' | 'desc'

export type SortKey = string | number

export type SortValue = string | number | boolean | Date | null | undefined

export type SortComparator<T> = (a: T, b: T) => number

export type AriaSort = 'ascending' | 'descending' | 'none'

/**
 * Per-column comparators. Tables with a fixed column set pass a record, tables with dynamic
 * columns (profile fields, inventory fields) pass a resolver that builds the comparator on demand.
 */
export type SortComparators<T, K extends SortKey> =
    | Partial<Record<K, SortComparator<T>>>
    | ((key: K) => SortComparator<T> | undefined)

export interface SortValueOptions {
    /** Where {@code null} and {@code undefined} end up. Defaults to {@code last}. */
    nulls?: 'first' | 'last'
}

export interface SortableState<K extends SortKey> {
    key: Ref<K>
    direction: Ref<SortDirection>
}

export interface UseSortableOptions<T, K extends SortKey> {
    items: MaybeRefOrGetter<T[]>
    comparators: SortComparators<T, K>
    initialKey: K
    /** Direction used initially and whenever the sort switches to another column. Defaults to {@code asc}. */
    initialDirection?: SortDirection
    /** Tie breaker applied when the active comparator reports equality. Never inverted. */
    fallback?: SortComparator<T>
    /** External state holder, e.g. when sorting is remembered per tab. Defaults to internal refs. */
    state?: SortableState<K>
}

export interface UseSortableReturn<T, K extends SortKey> {
    sortKey: Ref<K>
    direction: Ref<SortDirection>
    sorted: ComputedRef<T[]>
    toggle: (key: K) => void
    isActive: (key: K) => boolean
    icon: (key: K) => string
    ariaSort: (key: K) => AriaSort
}

const collator = new Intl.Collator('de', {sensitivity: 'base', numeric: true})

/**
 * Compares two raw column values. Strings are compared with the German collator, dates by their
 * timestamp, booleans as 0/1 and numbers numerically.
 */
export function compareSortValues(a: SortValue, b: SortValue, options: SortValueOptions = {}): number {
    const aMissing = a === null || a === undefined
    const bMissing = b === null || b === undefined
    if (aMissing || bMissing) {
        if (aMissing && bMissing) return 0
        const missingRank = options.nulls === 'first' ? -1 : 1
        return aMissing ? missingRank : -missingRank
    }
    const left = a instanceof Date ? a.getTime() : a
    const right = b instanceof Date ? b.getTime() : b
    if (typeof left === 'number' && typeof right === 'number') return left - right
    if (typeof left === 'boolean' || typeof right === 'boolean') return Number(left) - Number(right)
    return collator.compare(String(left), String(right))
}

/** Builds a comparator from an accessor returning the sortable value of a row. */
export function byValue<T>(accessor: (item: T) => SortValue, options?: SortValueOptions): SortComparator<T> {
    return (a, b) => compareSortValues(accessor(a), accessor(b), options)
}

/** Builds a comparator for accessors returning an ISO timestamp. */
export function byDate<T>(accessor: (item: T) => string | null | undefined, options?: SortValueOptions): SortComparator<T> {
    return byValue(item => {
        const raw = accessor(item)
        return raw ? new Date(raw) : null
    }, options)
}

/** Icon name for a header cell of a column that is either active or idle. */
export function sortIconFor(active: boolean, direction: SortDirection): string {
    if (!active) return 'sort'
    return direction === 'asc' ? 'sort-up' : 'sort-down'
}

/**
 * Sort key and direction state for a list, together with the sorted list itself.
 *
 * <p>The composable never knows what a column means: callers supply a comparator per column,
 * built from {@link byValue} or {@link byDate} or handwritten.
 */
export function useSortable<T, K extends SortKey = string>(options: UseSortableOptions<T, K>): UseSortableReturn<T, K> {
    const switchDirection = options.initialDirection ?? 'asc'
    const sortKey = options.state?.key ?? (ref(options.initialKey) as Ref<K>)
    const direction = options.state?.direction ?? ref<SortDirection>(switchDirection)

    function comparatorFor(key: K): SortComparator<T> | undefined {
        const source = options.comparators
        return typeof source === 'function' ? source(key) : source[key]
    }

    const sorted = computed(() => {
        const list = [...toValue(options.items)]
        const comparator = comparatorFor(sortKey.value)
        const fallback = options.fallback
        if (!comparator && !fallback) return list
        const factor = direction.value === 'asc' ? 1 : -1
        return list.sort((a, b) => {
            const primary = comparator ? comparator(a, b) * factor : 0
            if (primary !== 0 || !fallback) return primary
            return fallback(a, b)
        })
    })

    function isActive(key: K): boolean {
        return sortKey.value === key
    }

    function toggle(key: K) {
        if (isActive(key)) {
            direction.value = direction.value === 'asc' ? 'desc' : 'asc'
            return
        }
        sortKey.value = key
        direction.value = switchDirection
    }

    function icon(key: K): string {
        return sortIconFor(isActive(key), direction.value)
    }

    function ariaSort(key: K): AriaSort {
        if (!isActive(key)) return 'none'
        return direction.value === 'asc' ? 'ascending' : 'descending'
    }

    return {sortKey, direction, sorted, toggle, isActive, icon, ariaSort}
}
