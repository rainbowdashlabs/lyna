/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * One product and its add-ons, as a list shows them.
 *
 * `heads` are the entries for the product itself - one on the storefront, possibly several in a list
 * of licences - and empty when the list holds an add-on but nothing of the product it belongs to.
 */
export interface ProductFamily<T> {
    productId: number
    heads: T[]
    addons: T[]
}

/**
 * Groups a list by product family: each add-on goes under the product it belongs to, in the order
 * the list already had. A family takes the place of its first entry, whether that is the product
 * itself or one of its add-ons.
 */
export function productFamilies<T>(
    items: T[],
    productOf: (item: T) => number,
    parentOf: (item: T) => number | null | undefined,
): ProductFamily<T>[] {
    const byProduct = new Map<number, ProductFamily<T>>()
    for (const item of items) {
        const parent = parentOf(item)
        const productId = parent ?? productOf(item)
        let family = byProduct.get(productId)
        if (!family) {
            family = {productId, heads: [], addons: []}
            byProduct.set(productId, family)
        }
        if (parent == null) family.heads.push(item)
        else family.addons.push(item)
    }
    return [...byProduct.values()]
}
