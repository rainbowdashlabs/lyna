/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, test} from '@playwright/test'
import {signUp} from './fixtures/auth'
import {createProduct, deleteProduct, GUILD, operatorHeaders, operatorPage, uniqueName} from './fixtures/admin'

/**
 * Taking a product's trial from its page. The stack runs no bot, so every account is held to the
 * Lyna account rules; the guild's account age is set to nothing so a fresh account can be tried. The
 * seeded account has a proved address, a fresh signup does not.
 */
async function trialProduct(request: APIRequestContext, name: string): Promise<number> {
    const headers = await operatorHeaders(request)
    const settings = await (await request.get(`/api/admin/g/${GUILD}/settings`, {headers})).json()
    await request.put(`/api/admin/g/${GUILD}/settings`, {headers, data: {...settings, trialAccountMinutes: 0}})
    const id = await createProduct(request, name, false, {trial: true})
    const types = await (await request.get(`/api/admin/g/${GUILD}/download-types`, {headers})).json()
    const jar = types.find((type: {name: string}) => type.name === 'Jar')
    await request.post(`/api/admin/g/${GUILD}/products/${id}/downloads`, {
        headers,
        data: {typeId: jar.id, repository: 'releases', groupId: 'de.chojo', artifactId: 'e2e-plugin', classifier: null},
    })
    return id
}

test.describe('A product\'s trial', () => {
    test('an account with a proved address takes the trial once and gets a working link', async ({page, request}) => {
        const name = uniqueName('Trialled')
        const id = await trialProduct(request, name)
        await operatorPage(page)

        await page.goto(`/products/${id}?trial=1`)
        const trial = page.locator('#trial')
        await trial.getByRole('button', {name: 'Download once'}).click()
        const link = trial.getByRole('link', {name: /\.jar$/})
        await expect(link).toBeVisible()
        const file = await request.get(await link.getAttribute('href') as string)
        expect(file.ok()).toBe(true)

        await page.reload()
        await expect(trial.getByText('You have taken this product\'s trial already.')).toBeVisible()
        await deleteProduct(request, id, name)
    })

    test('an account without a proved address is told to verify one', async ({page, request}) => {
        const name = uniqueName('Guarded trial')
        const id = await trialProduct(request, name)
        await signUp(page, 'trier')

        await page.goto(`/products/${id}`)

        await expect(page.locator('#trial').getByText('Verify an email address on your account first')).toBeVisible()
        await deleteProduct(request, id, name)
    })

    test('a visitor is asked to sign in', async ({page, request}) => {
        const name = uniqueName('Visited trial')
        const id = await trialProduct(request, name)

        await page.goto(`/products/${id}`)

        await expect(page.locator('#trial').getByRole('link', {name: 'Sign in to try it'})).toBeVisible()
        await deleteProduct(request, id, name)
    })
})
