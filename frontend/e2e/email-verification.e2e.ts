/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {PASSWORD, signUp} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * Confirming an address. The stack sends no mail, so the link itself cannot be followed here - what
 * these pin is everything around it: that a fresh address counts as unconfirmed, that a change does
 * not take effect on its own, and that a bad token is refused rather than accepted.
 */
test.describe('Email verification', () => {
    test('a fresh account reads as unconfirmed', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('unverified'), password: PASSWORD},
        })
        const {token} = await signup.json()

        const me = await request.get('/api/account', {headers: {Authorization: `Bearer ${token}`}})

        const {account} = await me.json()
        expect(account.emailVerified).toBe(false)
    })

    test('asking to change the address does not change it', async ({request}) => {
        const original = uniqueEmail('keeps-address')
        const signup = await request.post('/api/auth/signup', {data: {email: original, password: PASSWORD}})
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        const change = await request.post('/api/account/email/change', {
            headers: authorized,
            data: {newEmail: uniqueEmail('not-yet-mine')},
        })
        expect(change.status()).toBe(202)

        const {account} = await (await request.get('/api/account', {headers: authorized})).json()
        expect(account.email).toBe(original)
        expect(account.emailVerified).toBe(false)
        expect(account.pendingEmail).not.toBe(original)
    })

    test('an address that is not one is refused', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('bad-address'), password: PASSWORD},
        })
        const {token} = await signup.json()

        const change = await request.post('/api/account/email/change', {
            headers: {Authorization: `Bearer ${token}`},
            data: {newEmail: 'not an address'},
        })

        expect(change.status()).toBe(400)
    })

    test('an address somebody else holds is answered as though it had been sent', async ({request}) => {
        const taken = uniqueEmail('already-taken')
        await request.post('/api/auth/signup', {data: {email: taken, password: PASSWORD}})
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('wants-it'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        const change = await request.post('/api/account/email/change', {
            headers: authorized,
            data: {newEmail: taken},
        })

        // Accepted rather than refused: saying "that one is taken" tells a stranger who has an
        // account here.
        expect(change.status()).toBe(202)
        // Signup issued a link for the account's own address, so something is pending either way -
        // what matters is that it is not the address somebody else holds.
        const {account} = await (await request.get('/api/account', {headers: authorized})).json()
        expect(account.pendingEmail).not.toBe(taken)
    })

    test('the change and resend endpoints are shut to a visitor without a session', async ({request}) => {
        expect((await request.post('/api/account/email/change',
            {data: {newEmail: uniqueEmail('anon')}})).status()).toBe(401)
        expect((await request.post('/api/account/email/resend-verification')).status()).toBe(401)
    })

    test('a token nobody issued is refused', async ({request}) => {
        const response = await request.post('/api/auth/email/verify', {data: {token: '0'.repeat(64)}})

        expect(response.status()).toBe(400)
    })

    test('the verify page says so when the link is no good', async ({page}) => {
        await page.goto(`/verify-email?token=${'0'.repeat(64)}`)

        await expect(page.getByText(/expired or has already been used/i)).toBeVisible()
    })

    test('the security page shows the address and whether it is confirmed', async ({page}) => {
        const account = await signUp(page, 'email-card')

        await page.goto('/account/security')

        // Scoped to the content: the layout names the signed-in account in its sidebar as well.
        await expect(page.getByRole('main').getByText(account.email).first()).toBeVisible()
        await expect(page.getByText('Not confirmed')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Change email'})).toBeVisible()
    })
})
