/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, type Page} from '@playwright/test'
import {expect, sessionCookieOf, test} from './fixtures/session'
import {logOut, PASSWORD, SESSION_COOKIE, sessionCookie, signUp} from './fixtures/auth'

/**
 * The session a browser holds: a cookie no script can read, renewed by being used, ended per device,
 * and found again through Discord when it has run out.
 *
 * <p>The stack renews a session's token on every request rather than once a day, so renewal is
 * something these can watch happen.
 */
test.describe('The session cookie', () => {
    test('is out of reach of scripts, kept off cross-site posts and scoped to the API', async ({page}) => {
        await signUp(page, 'cookie')

        const cookie = await sessionCookie(page)

        expect(cookie).not.toBeNull()
        expect(cookie!.httpOnly).toBe(true)
        expect(cookie!.sameSite).toBe('Lax')
        expect(cookie!.path).toBe('/api')
        expect(await page.evaluate(() => document.cookie)).not.toContain(SESSION_COOKIE)
        expect(await page.evaluate(() => window.localStorage.getItem('auth'))).toBeNull()
    })

    test('a change made with the cookie but not by the web client is refused', async ({signedUp}) => {
        const {api} = await signedUp('forged')

        const forged = await api.put('/api/account/username', {
            headers: {'X-Lyna-Client': 'elsewhere'},
            data: {username: 'forged'},
        })
        const genuine = await api.put('/api/account/username', {data: {username: 'genuine'}})

        expect(forged.status()).toBe(403)
        expect(genuine.ok()).toBe(true)
    })

    test('is renewed by being used, and stays the same session', async ({signedUp}) => {
        const {api} = await signedUp('renewed')
        const first = await sessionCookieOf(api)
        await new Promise(resolve => setTimeout(resolve, 1100))

        expect((await api.get('/api/auth/me')).ok()).toBe(true)
        const renewed = await sessionCookieOf(api)

        expect(renewed).toBeDefined()
        expect(renewed).not.toBe(first)
        const sessions = await (await api.get('/api/account/sessions')).json()
        expect(sessions).toHaveLength(1)
    })
})

test.describe('Signing out', () => {
    test('ends this device only: the account stays signed in everywhere else', async ({signedUp, signedIn}) => {
        const {email, api: laptop} = await signedUp('two-devices')
        const phone = await signedIn({email, password: PASSWORD})
        expect((await (await laptop.get('/api/account/sessions')).json())).toHaveLength(2)

        expect((await laptop.post('/api/auth/logout')).status()).toBe(204)

        expect((await laptop.get('/api/auth/me')).status()).toBe(401)
        expect(await sessionCookieOf(laptop)).toBeUndefined()
        expect((await phone.get('/api/auth/me')).ok()).toBe(true)
        expect((await (await phone.get('/api/account/sessions')).json())).toHaveLength(1)
    })

    test('ending another session from the list signs that device out', async ({signedUp, signedIn}) => {
        const {email, api: laptop} = await signedUp('ends-other')
        const phone = await signedIn({email, password: PASSWORD})
        const sessions = await (await laptop.get('/api/account/sessions')).json() as {jti: string, current: boolean}[]
        const other = sessions.find(session => !session.current)!

        expect((await laptop.delete(`/api/account/sessions/${other.jti}`)).status()).toBe(204)

        expect((await phone.get('/api/auth/me')).status()).toBe(401)
        expect((await laptop.get('/api/auth/me')).ok()).toBe(true)
    })
})

test.describe('A session from before the cookie', () => {
    test('is moved into the cookie on the next visit, without signing anybody out', async ({page, signedUp}) => {
        const {api} = await signedUp('adopted')
        const token = await sessionCookieOf(api)
        await page.goto('/', {waitUntil: 'networkidle'})
        await page.evaluate(stored => window.localStorage.setItem('auth', stored), token!)

        await page.goto('/account')

        await expect(page.getByRole('heading', {level: 1, name: 'Account'})).toBeVisible()
        await expect(page).toHaveURL(/\/account$/)
        expect(await sessionCookie(page)).not.toBeNull()
        expect(await page.evaluate(() => window.localStorage.getItem('auth'))).toBeNull()
    })

    test('is refused when it belongs to no live session', async ({request}) => {
        const response = await request.post('/api/auth/adopt', {headers: {Authorization: 'Bearer not.a.token'}})

        expect(response.status()).toBe(401)
    })
})

/**
 * Discord sign-in, finished against the stack's stand-in for Discord's API. The page a browser is
 * sent to for signing in is Discord's own, so the stories answer it themselves, the way Discord does
 * for somebody who granted the application before: straight back to the callback.
 */
test.describe('Signing in through Discord', () => {
    function discordUser(): string {
        return `9${Date.now()}${Math.floor(Math.random() * 1000).toString().padStart(3, '0')}`
    }

    async function startDiscord(api: APIRequestContext, next: string): Promise<string> {
        const start = await api.get(`/api/auth/discord/start?next=${encodeURIComponent(next)}`, {maxRedirects: 0})
        expect(start.status()).toBe(302)
        return new URL(start.headers()['location']).searchParams.get('state')!
    }

    /**
     * Stands in for Discord's sign-in page, which the stack cannot serve and the browser must not
     * reach. The start of every Discord sign-in is let through to the backend, which sets the state
     * cookie and names Discord's page; the browser is then sent straight on to the callback, the way
     * Discord sends somebody back who granted the application before. A page that moves on rather
     * than a redirect, because WebKit refuses to have a redirect made up for it.
     *
     * @return the Discord addresses the backend sent the browser to, in order
     */
    async function discordVouchesFor(page: Page, user: string): Promise<URL[]> {
        const sentTo: URL[] = []
        await page.route('**/api/auth/discord/start**', async route => {
            const response = await route.fetch({maxRedirects: 0})
            const discord = new URL(response.headers()['location'])
            sentTo.push(discord)
            const state = discord.searchParams.get('state')
            const callback = `/api/auth/discord/callback?code=user-${user}&state=${state}`
            await route.fulfill({
                status: 200,
                contentType: 'text/html',
                headers: {'set-cookie': response.headers()['set-cookie']},
                body: `<meta http-equiv="refresh" content="0;url=${callback}">`,
            })
        })
        return sentTo
    }

    async function signInWithDiscord(page: Page, user: string): Promise<URL[]> {
        const sentTo = await discordVouchesFor(page, user)
        await page.goto('/login', {waitUntil: 'networkidle'})
        await page.getByRole('link', {name: 'Continue with Discord'}).click()
        await expect(page.getByRole('heading', {level: 1, name: 'Account'})).toBeVisible()
        expect(sentTo[0].origin + sentTo[0].pathname).toBe('https://discord.com/oauth2/authorize')
        expect(sentTo[0].searchParams.get('prompt')).toBe('none')
        return sentTo
    }

    test('lands where it was asked to, signed in', async ({anonymous}) => {
        const api = await anonymous()
        const state = await startDiscord(api, '/account/security?tab=1')

        const callback = await api.get(`/api/auth/discord/callback?code=user-${discordUser()}&state=${state}`, {maxRedirects: 0})

        expect(callback.status()).toBe(302)
        expect(callback.headers()['location']).toBe('/account/security?tab=1')
        expect(await sessionCookieOf(api)).toBeDefined()
        expect((await api.get('/api/auth/me')).ok()).toBe(true)
    })

    test('will not be talked into sending somebody off the site', async ({anonymous}) => {
        const api = await anonymous()
        const state = await startDiscord(api, '//example.invalid/')

        const callback = await api.get(`/api/auth/discord/callback?code=user-${discordUser()}&state=${state}`, {maxRedirects: 0})

        expect(callback.headers()['location']).toBe('/account')
    })

    test('a Discord that refuses sends the browser to the sign-in page', async ({anonymous}) => {
        const api = await anonymous()
        const state = await startDiscord(api, '/account')

        const callback = await api.get(`/api/auth/discord/callback?error=access_denied&state=${state}`, {maxRedirects: 0})

        expect(callback.headers()['location']).toBe('/login')
    })

    test('a session that ran out goes back through Discord to the same page, with no click', async ({page}) => {
        const sentTo = await signInWithDiscord(page, discordUser())
        await page.waitForLoadState('networkidle')

        await page.context().clearCookies({name: SESSION_COOKIE})
        await page.goto('/account/licenses')

        await expect.poll(() => sentTo.length).toBe(2)
        await expect(page).toHaveURL(/\/account\/licenses$/)
        await expect.poll(async () => await sessionCookie(page)).not.toBeNull()
    })

    test('logging out does not bounce straight back in through Discord', async ({page}) => {
        const sentTo = await signInWithDiscord(page, discordUser())

        await logOut(page)
        await expect(page).toHaveURL(/\/login/)
        await page.goto('/account')

        await expect(page).toHaveURL(/\/login/)
        expect(sentTo).toHaveLength(1)
    })
})
