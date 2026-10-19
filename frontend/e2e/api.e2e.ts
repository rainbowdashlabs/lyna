/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, sessionCookieOf, test} from './fixtures/session'
import {PASSWORD, SESSION_COOKIE} from './fixtures/auth'
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

    test('dates are ISO-8601 text, except on the public releases endpoint, which keeps seconds since the epoch', async ({request}) => {
        const products = await (await request.get('/api/v1/products')).json() as {id: number, name: string}[]
        const freebie = products.find(product => product.name === 'E2E Freebie')!

        const versions = await (await request.get(`/api/v1/products/${freebie.id}/release-types/STABLE/versions`)).json()
        expect(versions[0].publishedAt).toBe('2026-02-01T12:00:00Z')

        const assets = await (await request.get(`/api/v1/releases/${freebie.id}/1`)).json()
        expect(typeof assets[0].published).toBe('number')
    })

    test('the account endpoints refuse a request carrying no session', async ({request}) => {
        const response = await request.get('/api/account')

        expect(response.status()).toBe(401)
    })

    test('the account endpoints refuse a session cookie that was not signed here', async ({request}) => {
        const response = await request.get('/api/account', {
            headers: {Cookie: `${SESSION_COOKIE}=not.a.real.token`},
        })

        expect(response.status()).toBe(401)
    })

    test('signing up answers with the account and a session cookie, never a token', async ({anonymous}) => {
        const api = await anonymous()

        const signup = await api.post('/api/auth/signup', {data: {email: uniqueEmail('api'), password: PASSWORD}})

        expect(signup.ok()).toBe(true)
        const body = await signup.json()
        expect(body.token).toBeUndefined()
        expect(body.account.hasPassword).toBe(true)
        expect(await sessionCookieOf(api)).toBeDefined()
        expect((await (await api.get('/api/account')).json()).account.hasPassword).toBe(true)
    })

    test('a session token is not accepted as a Bearer header any more', async ({request, signedUp}) => {
        const {api} = await signedUp('bearer')
        const token = await sessionCookieOf(api)

        const response = await request.get('/api/account', {headers: {Authorization: `Bearer ${token}`}})

        expect(response.status()).toBe(401)
    })

    test('the admin guild list is empty for an account with no Discord link', async ({signedUp}) => {
        const {api} = await signedUp('no-guilds')

        const response = await api.get('/api/admin/guilds')

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
        expect(location.searchParams.get('prompt')).toBe('none')
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
