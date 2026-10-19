/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, uniqueName} from './fixtures/admin'

/**
 * The storefront as tiles or as a list that says what each product is about, and in which order.
 */
test.describe('The storefront', () => {
    test('a list shows the first lines of each description, and stays a list on the next visit', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Described')
        const id = await createProduct(operator, name, true)
        await operator.put(`/api/admin/g/${GUILD}/products/${id}/description`, {
            data: {description: '# Heading\n\nA **listed** plugin that [does things](https://example.invalid).\n\nMore.'},
        })

        await page.goto('/')
        await page.getByPlaceholder('Search plugins').fill(name)
        await expect(page.getByText('A listed plugin that does things.')).toHaveCount(0)
        await page.getByRole('button', {name: 'List', exact: true}).click()

        await expect(page.getByText('A listed plugin that does things.')).toBeVisible()
        await page.reload()
        await page.getByPlaceholder('Search plugins').fill(name)
        await expect(page.getByText('A listed plugin that does things.')).toBeVisible()
        await deleteProduct(operator, id, name)
    })

    test('products are in name order unless asked for the newest release first', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const stem = uniqueName('Sorted')
        const zeta = await createProduct(operator, `${stem} zeta`, true)
        const alpha = await createProduct(operator, `${stem} alpha`, true)

        await page.goto('/')
        await page.getByPlaceholder('Search plugins').fill(stem)
        const names = page.getByRole('heading', {name: stem})
        await expect(names).toHaveText([`${stem} alpha`, `${stem} zeta`])

        await page.getByLabel('Sort by').selectOption('updated')
        await expect(names).toHaveText([`${stem} alpha`, `${stem} zeta`])
        await deleteProduct(operator, zeta, `${stem} zeta`)
        await deleteProduct(operator, alpha, `${stem} alpha`)
    })
})
