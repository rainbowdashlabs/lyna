/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, test} from '@playwright/test'
import {PASSWORD} from './fixtures/auth'

/**
 * Where Discord sign-in lands once the backend has a session for somebody.
 *
 * <p>The stack cannot finish a real exchange with Discord, so these start where the backend's redirect
 * leaves off: a session in the fragment, and the page that keeps it.
 */
test.describe('Arriving back from Discord', () => {
    async function session(request: APIRequestContext): Promise<string> {
        const login = await request.post('/api/auth/login', {
            data: {email: 'entitled@example.invalid', password: PASSWORD},
        })
        return (await login.json()).token
    }

    test('keeps the session and carries on to where it was going', async ({page, request}) => {
        const token = await session(request)

        await page.goto(`/auth/discord#token=${encodeURIComponent(token)}&next=${encodeURIComponent('/account/security')}`)

        await expect(page).toHaveURL(/\/account\/security$/)
        expect(await page.evaluate(() => localStorage.getItem('auth'))).toBe(token)
    })

    test('does not leave the session sitting in the address bar', async ({page, request}) => {
        const token = await session(request)

        await page.goto(`/auth/discord#token=${encodeURIComponent(token)}`)

        await expect(page).toHaveURL(/\/account$/)
        expect(page.url()).not.toContain(token)
    })

    test('will not be talked into sending somebody off the site', async ({page, request}) => {
        const token = await session(request)

        await page.goto(`/auth/discord#token=${encodeURIComponent(token)}&next=${encodeURIComponent('//example.invalid/')}`)

        await expect(page).toHaveURL(/\/account$/)
    })

    test('says so when it arrives with nothing to sign in with', async ({page}) => {
        await page.goto('/auth/discord')

        await expect(page.getByText('Discord sent you back without a session.')).toBeVisible()
    })
})
