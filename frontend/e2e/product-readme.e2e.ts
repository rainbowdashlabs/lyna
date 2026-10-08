/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {createProduct, deleteProduct, GUILD, operatorHeaders, operatorPage, uniqueName} from './fixtures/admin'

/**
 * A product page showing the project's GitHub README. The stack's stand-in answers for
 * e2e-owner/e2e-plugin the way GitHub would.
 */
const REPOSITORY = 'https://github.com/e2e-owner/e2e-plugin'

test.describe('A product page from the README', () => {
    test('a product with no description shows its README, with links made absolute and a way to GitHub', async ({page, request}) => {
        const name = uniqueName('Readme')
        const id = await createProduct(request, name, false, {url: REPOSITORY})

        await page.goto(`/products/${id}`)

        await expect(page.getByRole('heading', {name: 'E2E Plugin from GitHub'})).toBeVisible()
        await expect(page.getByRole('img', {name: 'logo'})).toHaveAttribute(
            'src', 'https://raw.githubusercontent.com/e2e-owner/e2e-plugin/HEAD/assets/logo.png')
        await expect(page.getByRole('link', {name: 'the setup guide'})).toHaveAttribute(
            'href', 'https://github.com/e2e-owner/e2e-plugin/blob/HEAD/docs/setup.md')
        await expect(page.getByRole('link', {name: 'From the project\'s README on GitHub'})).toBeVisible()

        const download = page.getByRole('link', {name: 'Download the latest release'})
        await expect(download).toHaveAttribute('href', `/products/${id}?download=1`)
        await download.click()
        await expect(page.getByRole('heading', {name, level: 3})).toBeVisible()
        await deleteProduct(request, id, name)
    })

    test('a product with its own words keeps them until the README is switched on in the admin area', async ({page, request}) => {
        const name = uniqueName('Worded')
        const id = await createProduct(request, name, false, {url: REPOSITORY})
        await request.put(`/api/admin/g/${GUILD}/products/${id}/description`, {
            headers: await operatorHeaders(request),
            data: {description: '# Written in Lyna'},
        })
        await page.goto(`/products/${id}`)
        await expect(page.getByRole('heading', {name: 'Written in Lyna'})).toBeVisible()

        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=page`)
        await page.locator('label').filter({hasText: 'Show the GitHub README as the page'}).getByRole('switch').click()
        await expect(page.getByText('The page shows the README.')).toBeVisible()

        await page.goto(`/products/${id}`)
        await expect(page.getByRole('heading', {name: 'E2E Plugin from GitHub'})).toBeVisible()
        await deleteProduct(request, id, name)
    })
})
