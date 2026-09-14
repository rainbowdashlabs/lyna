/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {PASSWORD} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * The contract the browser and the client updaters both speak, asserted through the proxy the
 * browser uses rather than against the backend directly, so the route rule is covered as well.
 */
test.describe('The HTTP API through the frontend proxy', () => {
    test('the product list answers without a session', async ({request}) => {
        const response = await request.get('/api/v1/products')

        expect(response.ok()).toBe(true)
        expect(Array.isArray(await response.json())).toBe(true)
    })

    test('the account endpoints refuse a request carrying no token', async ({request}) => {
        const response = await request.get('/api/account')

        expect(response.status()).toBe(401)
    })

    test('the account endpoints refuse a token that was not signed here', async ({request}) => {
        const response = await request.get('/api/account', {
            headers: {Authorization: 'Bearer not.a.real.token'},
        })

        expect(response.status()).toBe(401)
    })

    test('signing up answers with a token that the account endpoint then accepts', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('api'), password: PASSWORD},
        })
        expect(signup.ok()).toBe(true)
        const {token} = await signup.json()

        const account = await request.get('/api/account', {headers: {Authorization: `Bearer ${token}`}})

        expect(account.ok()).toBe(true)
        expect((await account.json()).account.hasPassword).toBe(true)
    })

    test('a token stops being accepted once it is logged out', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('revoked'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}
        expect((await request.get('/api/account', {headers: authorized})).ok()).toBe(true)

        await request.post('/api/auth/logout', {headers: authorized})

        expect((await request.get('/api/account', {headers: authorized})).status()).toBe(401)
    })

    test('the admin guild list is empty for an account with no Discord link', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('no-guilds'), password: PASSWORD},
        })
        const {token} = await signup.json()

        const response = await request.get('/api/admin/guilds', {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.ok()).toBe(true)
        expect(await response.json()).toEqual([])
    })
})
