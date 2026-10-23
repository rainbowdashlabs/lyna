/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {signUp} from './fixtures/auth'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/**
 * Taking a product's trial from its page. The stack runs no bot, so every account is held to the
 * Lyna account rules; the guild's account age is set to nothing so a fresh account can be tried. The
 * seeded account has a proved address, a fresh signup does not.
 */
async function trialProduct(operator: APIRequestContext, name: string): Promise<number> {
    const settings = await (await operator.get(`/api/admin/g/${GUILD}/settings`)).json()
    await operator.put(`/api/admin/g/${GUILD}/settings`, {data: {...settings, trialAccountMinutes: 0}})
    const id = await createProduct(operator, name, false, {trial: true})
    const types = await (await operator.get(`/api/admin/g/${GUILD}/download-types`)).json()
    const jar = types.find((type: {name: string}) => type.name === 'Jar')
    await operator.post(`/api/admin/g/${GUILD}/products/${id}/downloads`, {
        data: {typeId: jar.id, repository: 'releases', groupId: 'de.chojo', artifactId: 'e2e-plugin', classifier: null},
    })
    return id
}

test.describe('A product\'s trial', () => {
    test('an account with a proved address takes the trial once and gets a working link', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Trialled')
        const id = await trialProduct(operator, name)
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
        await deleteProduct(operator, id, name)
    })

    test('an account without a proved address is told to verify one', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Guarded trial')
        const id = await trialProduct(operator, name)
        await signUp(page, 'trier')

        await page.goto(`/products/${id}`)

        await expect(page.locator('#trial').getByText('Verify an email address on your account first')).toBeVisible()
        await deleteProduct(operator, id, name)
    })

    test('a visitor is asked to sign in', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Visited trial')
        const id = await trialProduct(operator, name)

        await page.goto(`/products/${id}`)

        await expect(page.locator('#trial').getByRole('link', {name: 'Sign in to try it'})).toBeVisible()
        await deleteProduct(operator, id, name)
    })
})

test.describe('The trial admin page', () => {
    test('lists the products that offer a trial and no others', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const trialled = uniqueName('Trial listed')
        const plain = uniqueName('Trial unlisted')
        const trialledId = await createProduct(operator, trialled, false, {trial: true})
        const plainId = await createProduct(operator, plain)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/trial`)

        await expect(page.getByRole('listitem').filter({hasText: trialled})).toHaveCount(1)
        await expect(page.getByRole('listitem').filter({hasText: plain})).toHaveCount(0)
        await deleteProduct(operator, trialledId, trialled)
        await deleteProduct(operator, plainId, plain)
    })
})
