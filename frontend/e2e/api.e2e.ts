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

/**
 * Discord sign-in, asked for through the frontend as a browser would.
 *
 * <p>The end-to-end stack names a Discord application that does not exist, which is enough to see
 * the redirect leave: the frontend must hand it to the browser rather than follow it itself. Following
 * it served Discord's page under this address, and nobody ever came back from Discord.
 */
test.describe('Discord sign-in', () => {
    test('sends the browser to Discord rather than fetching Discord itself', async ({request}) => {
        const response = await request.get('/api/auth/discord/start', {maxRedirects: 0})

        expect(response.status()).toBe(302)
        const location = new URL(response.headers()['location'])
        expect(location.origin).toBe('https://discord.com')
        expect(location.searchParams.get('client_id')).toBe('4242000000000000001')
        expect(location.searchParams.get('scope')).toBe('identify email')
    })

    test('remembers who it sent, so the way back can be checked', async ({request}) => {
        const response = await request.get('/api/auth/discord/start', {maxRedirects: 0})

        const state = new URL(response.headers()['location']).searchParams.get('state')
        expect(response.headers()['set-cookie']).toContain(`lyna_oauth_state=${state}`)
    })

    test('turns away a return it did not send', async ({request}) => {
        const response = await request.get('/api/auth/discord/callback?code=x&state=y', {maxRedirects: 0})

        expect(response.status()).toBe(400)
    })
})
