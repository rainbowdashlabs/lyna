/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {createProduct, deleteProduct, GUILD, operatorHeaders, operatorPage, uniqueName} from './fixtures/admin'
import {uniqueEmail} from './fixtures/unique'

/**
 * A product's mailing made and its license mail sent from the product page. The stack sends no mail,
 * so what the story sees is the license the sending issued.
 */
test.describe('A product\'s mailing', () => {
    test('a mailing is made, a license is issued and mailed, and sending again resends that license', async ({page, request}) => {
        const name = uniqueName('Mailed')
        const address = uniqueEmail('buyer')
        const id = await createProduct(request, name)
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=mailing`)

        await page.getByLabel('Product name as the mail says it').fill(name)
        await page.getByRole('button', {name: 'Create mailing'}).click()
        await expect(page.getByText(`Mail for ${name}.`)).toBeVisible()

        await page.getByLabel('Email address').fill(address)
        await page.getByLabel('Name of the recipient').fill('Story Buyer')
        await page.getByRole('button', {name: 'Issue license and send'}).click()
        await expect(page.getByText('Sent.')).toBeVisible()

        await page.getByLabel('Email address').fill(address)
        await page.getByLabel('Name of the recipient').fill('Story Buyer')
        await page.getByRole('button', {name: 'Issue license and send'}).click()
        await expect(page.getByText('Sent.')).toBeVisible()

        const licenses = await (await request.get(`/api/admin/g/${GUILD}/licenses`, {headers: await operatorHeaders(request)})).json()
        expect(licenses.filter((license: {identifier: string}) => license.identifier === address)).toHaveLength(1)
        await deleteProduct(request, id, name)
    })
})
