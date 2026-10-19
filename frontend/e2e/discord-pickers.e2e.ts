/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/**
 * Roles and channels picked by name. The stack runs without a bot, so what it can show is the
 * fallback: the answer says nobody is connected, and the page asks for the id instead.
 */
test.describe('Picking roles and channels', () => {
    test('without a bot the directory says so', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)

        expect(await (await operator.get(`/api/admin/g/${GUILD}/roles`)).json()).toEqual({connected: false, roles: []})
        expect(await (await operator.get(`/api/admin/g/${GUILD}/channels`)).json()).toEqual({connected: false, channels: []})
        expect(await (await operator.get(`/api/admin/g/${GUILD}/channels?for=renaming`)).json()).toEqual({connected: false, channels: []})
    })

    test('is shut to a visitor who is not signed in', async ({anonymous}) => {
        const visitor = await anonymous()

        expect((await visitor.get(`/api/admin/g/${GUILD}/roles`)).status()).toBe(401)
        expect((await visitor.get(`/api/admin/g/${GUILD}/channels`)).status()).toBe(401)
    })

    test('the release channel falls back to an id, and says why', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Announced')
        const id = await createProduct(operator, name)
        await operator.post(`/api/admin/g/${GUILD}/products/${id}/release-webhook`)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=announcements`)

        await expect(page.getByPlaceholder('Channel id').first()).toBeVisible()
        await expect(page.getByText('No bot is connected, so there are no names to choose from. Enter the id.').first()).toBeVisible()
        await deleteProduct(operator, id, name)
    })
})
