/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/** Saves a product with everything left as it was created, apart from the parent. */
async function setParent(operator: APIRequestContext, id: number, name: string, parentId: number | null) {
    return operator.put(`/api/admin/g/${GUILD}/products/${id}`, {
        data: {
            name, url: null, roleId: '99', free: true, trial: false,
            iconUrl: null, butlerId: null, wikiUrl: null, reviewUrl: null, parentId,
        },
    })
}

/**
 * Add-ons: set from the admin form, listed beneath the product they belong to, and named on each
 * other's pages.
 */
test.describe('Add-ons', () => {
    test('an add-on set in the admin form follows its parent in the storefront list and names it on its page', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const stem = uniqueName('Family')
        const parentName = `${stem} zeta`
        const addonName = `${stem} alpha`
        const siblingName = `${stem} beta`
        const parent = await createProduct(operator, parentName, true)
        const addon = await createProduct(operator, addonName, true)
        const sibling = await createProduct(operator, siblingName, true)
        expect((await setParent(operator, sibling, siblingName, parent)).status()).toBe(204)

        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/products/${addon}`)
        await page.getByTestId('product-parent').selectOption({label: parentName})
        await page.getByRole('button', {name: 'Save', exact: true}).click()
        await expect(page.getByText('Saved.')).toBeVisible()

        await page.goto('/')
        await page.getByPlaceholder('Search plugins').fill(stem)
        await page.getByRole('button', {name: 'List', exact: true}).click()
        const names = page.locator('main li a[href^="/products/"]')
        await expect(names).toHaveText([parentName, addonName, siblingName])
        await expect(page.getByText(`Requires ${parentName}`)).toHaveCount(2)

        await page.goto(`/products/${addon}`)
        const family = page.getByTestId('product-family')
        await expect(family.getByText('Belongs to')).toBeVisible()
        await expect(family.getByRole('link', {name: parentName})).toBeVisible()
        await expect(family.getByText('Similar')).toBeVisible()
        await expect(family.getByRole('link', {name: siblingName})).toBeVisible()

        await page.goto(`/products/${parent}`)
        await expect(page.getByTestId('product-family').getByRole('link', {name: addonName})).toBeVisible()

        await deleteProduct(operator, sibling, siblingName)
        await deleteProduct(operator, addon, addonName)
        await deleteProduct(operator, parent, parentName)
    })

    test('a family is one level deep', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const stem = uniqueName('Level')
        const parent = await createProduct(operator, `${stem} parent`, true)
        const addon = await createProduct(operator, `${stem} addon`, true)
        const other = await createProduct(operator, `${stem} other`, true)
        expect((await setParent(operator, addon, `${stem} addon`, parent)).status()).toBe(204)

        expect((await setParent(operator, other, `${stem} other`, addon)).status()).toBe(400)
        expect((await setParent(operator, parent, `${stem} parent`, other)).status()).toBe(400)
        expect((await setParent(operator, addon, `${stem} addon`, addon)).status()).toBe(400)

        await deleteProduct(operator, other, `${stem} other`)
        await deleteProduct(operator, addon, `${stem} addon`)
        await deleteProduct(operator, parent, `${stem} parent`)
    })
})
