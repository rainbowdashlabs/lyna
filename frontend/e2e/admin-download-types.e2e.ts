/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {GUILD, operatorPage, uniqueName} from './fixtures/admin'

/**
 * Download types, managed on their own page. The seed's "Jar" type is offered by its products, which
 * is what keeps it from being deleted.
 */
test.describe('The download types page', () => {
    test('a type is made, renamed and deleted', async ({page}) => {
        const name = uniqueName('Type')
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/download-types`)

        await page.getByLabel('Name').first().fill(name)
        await page.getByLabel('Description').first().fill('made by a story')
        await page.getByRole('button', {name: 'Create'}).click()

        const row = page.getByRole('listitem', {name, exact: true})
        await expect(row).toBeVisible()
        await row.getByLabel('Name').fill(`${name} renamed`)
        await row.getByRole('button', {name: 'Save'}).click()
        const renamed = page.getByRole('listitem', {name: `${name} renamed`, exact: true})
        await expect(renamed).toBeVisible()

        await renamed.getByRole('button', {name: 'Delete'}).click()
        await expect(renamed).toHaveCount(0)
    })

    test('a type a product still offers is not deleted, and the page says who offers it', async ({page}) => {
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/download-types`)

        const jar = page.getByRole('listitem', {name: 'Jar', exact: true})
        await jar.getByRole('button', {name: 'Delete'}).click()

        await expect(jar.getByText(/Still offered by .*E2E Freebie/)).toBeVisible()
    })
})
