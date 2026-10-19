/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/** How the storefront lays its products out. */
export type StorefrontView = 'tiles' | 'list'

/** The order the storefront shows its products in. */
export type StorefrontSort = 'name' | 'updated'

const KEY = 'storefront.view'

/**
 * The layout this browser chose last, or tiles. Kept in the browser only: it is a preference of
 * whoever sits in front of it, not of the account, and storage that is blocked or empty just means
 * tiles.
 */
export function rememberedView(): StorefrontView {
    try {
        return localStorage.getItem(KEY) === 'list' ? 'list' : 'tiles'
    } catch {
        return 'tiles'
    }
}

export function rememberView(view: StorefrontView): void {
    try {
        localStorage.setItem(KEY, view)
    } catch {
        return
    }
}

/**
 * The products in the order asked for: by name, or newest release first with the products that have
 * none at the end, by name among themselves.
 */
export function sortProducts<T extends {name: string, updatedAt: string | null}>(products: T[], sort: StorefrontSort): T[] {
    const byName = (a: T, b: T) => a.name.localeCompare(b.name, undefined, {sensitivity: 'base'})
    if (sort === 'name') return [...products].sort(byName)
    return [...products].sort((a, b) => {
        if (a.updatedAt === b.updatedAt) return byName(a, b)
        if (!a.updatedAt) return 1
        if (!b.updatedAt) return -1
        return b.updatedAt.localeCompare(a.updatedAt)
    })
}
