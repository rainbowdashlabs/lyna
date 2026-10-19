/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, type Page} from '@playwright/test'
import {logIn, PASSWORD} from './auth'

/** The guild the seed puts its products in. */
export const GUILD = '4242'

/** The seeded account, an operator of the stack by the Discord id it is linked to. */
export const OPERATOR = {email: 'entitled@example.invalid', password: PASSWORD}

/** Signs the operator in on this page. */
export async function operatorPage(page: Page): Promise<void> {
    await logIn(page, OPERATOR)
    await expect(page).toHaveURL(/\/account$/)
}

/**
 * Creates a product of its own for a story, through the operator's session, under a name nobody else
 * uses. Premium unless asked, so the storefront's free tiles stay the seed's, which other stories
 * count on.
 *
 * @return its id
 */
export async function createProduct(
    operator: APIRequestContext, name: string, free = false, options: {url?: string, trial?: boolean} = {}): Promise<number> {
    const created = await operator.post(`/api/admin/g/${GUILD}/products`, {
        data: {name, url: options.url ?? null, roleId: '99', free, trial: options.trial ?? false},
    })
    expect(created.status()).toBe(201)
    return (await created.json()).id
}

/** Takes a story's product away again, so the catalogue other stories read stays the seed's. */
export async function deleteProduct(operator: APIRequestContext, id: number, name: string): Promise<void> {
    await operator.delete(`/api/admin/g/${GUILD}/products/${id}`, {data: {confirmName: name}})
}

/** A product name no other story or run uses. */
export function uniqueName(prefix: string): string {
    return `${prefix} ${Date.now()}-${Math.random().toString(36).slice(2, 7)}`
}
