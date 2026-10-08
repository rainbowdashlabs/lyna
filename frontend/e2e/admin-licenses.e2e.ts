/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {createProduct, deleteProduct, GUILD, operatorHeaders, operatorPage, uniqueName} from './fixtures/admin'

/**
 * Licenses found, given release types and ended on the licenses page, and Ko-fi codes unlinked.
 */
test.describe('Managing licenses', () => {
    test('a license is found by search, reaches a release type, and is deleted after a second click', async ({page, request}) => {
        const name = uniqueName('Licensed')
        const identifier = uniqueName('buyer').replace(' ', '-')
        const id = await createProduct(request, name)
        await request.post(`/api/admin/g/${GUILD}/licenses`, {
            headers: await operatorHeaders(request),
            data: {productId: id, identifier},
        })
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/licenses`)

        await page.getByPlaceholder('Search by product, identifier, id or owner').fill(identifier)
        const row = page.getByRole('listitem', {name: identifier})
        await expect(row).toHaveCount(1)
        await row.getByRole('button', {name: 'Manage'}).click()
        await row.locator('label').filter({hasText: 'DEV'}).getByRole('switch').click()
        await expect(row.locator('label').filter({hasText: 'DEV'}).getByRole('switch')).toHaveAttribute('aria-checked', 'true')

        await page.reload()
        await page.getByPlaceholder('Search by product, identifier, id or owner').fill(identifier)
        await row.getByRole('button', {name: 'Manage'}).click()
        await expect(row.locator('label').filter({hasText: 'DEV'}).getByRole('switch')).toHaveAttribute('aria-checked', 'true')

        await row.getByRole('button', {name: 'Delete license'}).click()
        await row.getByRole('button', {name: 'Really delete'}).click()
        await expect(row).toHaveCount(0)
        await deleteProduct(request, id, name)
    })

    test('a Ko-fi code is unlinked from the Ko-fi page', async ({page, request}) => {
        const name = uniqueName('Sold')
        const code = `e2e${Date.now()}`
        const id = await createProduct(request, name)
        await request.post(`/api/admin/g/${GUILD}/kofi`, {headers: await operatorHeaders(request), data: {linkCode: code, productId: id}})
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/kofi`)

        const mapping = page.getByRole('listitem').filter({hasText: code})
        await mapping.getByRole('button', {name: 'Remove'}).click()

        await expect(mapping).toHaveCount(0)
        await deleteProduct(request, id, name)
    })
})
