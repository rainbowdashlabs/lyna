/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {PASSWORD, signUp} from './fixtures/auth'

/**
 * Who may reach the instance area, and who may hand it out.
 *
 * <p>Every account these stories make is a fresh one that administers nothing, so what they pin is
 * the floor: nobody gets in, and the endpoints that would grant it are shut to everybody. That is the
 * state a fresh instance is in, and the one a mistake here would quietly open up.
 *
 * <p>The stack does name one operator - the seeded account, by the Discord id it is linked to - which
 * is what {@code administering without a bot} below rests on.
 */
test.describe('The operator endpoints', () => {
    test('are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.get('/api/admin/instance/operators')).status()).toBe(401)
        expect((await request.post('/api/admin/instance/operators',
            {data: {discordId: '123456789'}})).status()).toBe(401)
        expect((await request.delete('/api/admin/instance/operators/123456789')).status()).toBe(401)
    })

    test('are shut to somebody signed in who administers nothing', async ({signedUp}) => {
        const {api} = await signedUp('not-operator')

        expect((await api.get('/api/admin/instance/operators')).status()).toBe(404)
        expect((await api.post('/api/admin/instance/operators',
            {data: {discordId: '123456789'}})).status()).toBe(404)
        expect((await api.delete('/api/admin/instance/operators/123456789')).status()).toBe(404)
    })

    test('an ordinary account cannot grant itself the instance', async ({signedUp}) => {
        const {api} = await signedUp('self-grant')

        const granted = await api.post('/api/admin/instance/operators', {
            data: {discordId: '4242424242'},
        })

        expect(granted.status()).toBe(404)
        const guilds = await api.get('/api/admin/guilds')
        expect(await guilds.json()).toEqual([])
    })

    test('say whether the caller is an operator', async ({signedIn, signedUp}) => {
        const operator = await signedIn({email: 'entitled@example.invalid', password: PASSWORD})
        const {api} = await signedUp('access')

        expect((await (await operator.get('/api/admin/access')).json()).operator).toBe(true)
        expect(await (await api.get('/api/admin/access')).json()).toEqual({operator: false, guilds: []})
    })
})

test.describe('The operators page', () => {
    test('sends an account that administers nothing back to its own area', async ({page}) => {
        await signUp(page, 'operators-page')

        await page.goto('/admin/instance/operators')

        await expect(page.getByText(/Operator access required/i)).toBeVisible()
    })

    /**
     * This stack runs no bot, which used to mean the admin area answered 404 to everybody: the guard
     * asked the gateway for the guild before it asked who was calling, and with no gateway there was
     * no guild to find. An operator holds the whole instance and has no membership to check, so
     * there is nothing for the gateway to answer.
     */
    test('an operator administers a guild even with no bot connected', async ({signedIn}) => {
        const operator = await signedIn({email: 'entitled@example.invalid', password: PASSWORD})

        const products = await operator.get('/api/admin/g/4242/products')

        expect(products.status()).toBe(200)
        expect(Array.isArray(await products.json())).toBe(true)
    })

    test('somebody who is not an operator still gets nothing, bot or no bot', async ({signedUp}) => {
        const {api} = await signedUp('not-an-operator')

        const products = await api.get('/api/admin/g/4242/products')

        expect(products.status()).toBe(404)
    })
})