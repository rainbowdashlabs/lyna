/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {createProduct, deleteProduct, GUILD, operatorPage, uniqueName} from './fixtures/admin'

/**
 * A product's own admin page: reached from the list, its page written there, and deleted from there.
 */
test.describe('A product\'s admin page', () => {
    test('the list leads to the product, whose page is written beside a preview and then shown publicly', async ({page, request}) => {
        const name = uniqueName('Paged')
        const id = await createProduct(request, name)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/products`)
        await page.getByRole('link', {name: new RegExp(name)}).click()
        await expect(page.getByRole('heading', {name})).toBeVisible()

        await page.getByRole('tab', {name: 'Page'}).click()
        await page.locator('textarea').fill('# Written here\n\nWith **markdown**.')
        await expect(page.getByRole('heading', {name: 'Written here'})).toBeVisible()
        await page.getByRole('button', {name: 'Save page'}).click()
        await expect(page.getByText('Saved.')).toBeVisible()

        await page.goto(`/products/${id}`)
        await expect(page.getByRole('heading', {name: 'Written here'})).toBeVisible()
        await deleteProduct(request, id, name)
    })

    test('a product is deleted only once its name is typed out', async ({page, request}) => {
        const name = uniqueName('Doomed')
        const id = await createProduct(request, name)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/products/${id}`)
        const remove = page.getByRole('button', {name: 'Delete product'})
        await expect(remove).toBeDisabled()
        await page.getByLabel('Type the product\'s name to confirm').fill(name)
        await remove.click()

        await expect(page).toHaveURL(new RegExp(`/admin/g/${GUILD}/products$`))
        expect((await request.get(`/api/v1/products/${id}`)).status()).toBe(404)
    })
})
