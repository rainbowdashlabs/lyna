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
 * these pin is everything around it: that a claimed address counts for nothing until it is confirmed,
 * that claiming one somebody else holds is answered as though it had been sent, and that a bad token
 * is refused rather than accepted.
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

    test('claiming another address leaves the one the account is written to alone', async ({request}) => {
        const original = uniqueEmail('keeps-address')
        const signup = await request.post('/api/auth/signup', {data: {email: original, password: PASSWORD}})
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        const claim = await request.post('/api/account/emails', {
            headers: authorized,
            data: {address: uniqueEmail('not-yet-mine')},
        })
        expect(claim.status()).toBe(202)

        const {account} = await (await request.get('/api/account', {headers: authorized})).json()
        expect(account.email).toBe(original)
        expect(account.emailVerified).toBe(false)

        const addresses = await (await request.get('/api/account/emails', {headers: authorized})).json()
        expect(addresses).toHaveLength(2)
        expect(addresses.every((a: {verified: boolean}) => !a.verified)).toBe(true)
    })

    test('an address that is not one is refused', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('bad-address'), password: PASSWORD},
        })
        const {token} = await signup.json()

        const claim = await request.post('/api/account/emails', {
            headers: {Authorization: `Bearer ${token}`},
            data: {address: 'not an address'},
        })

        expect(claim.status()).toBe(400)
    })

    test('an address somebody else holds is answered as though it had been sent', async ({request}) => {
        const taken = uniqueEmail('already-taken')
        await request.post('/api/auth/signup', {data: {email: taken, password: PASSWORD}})
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('wants-it'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        const claim = await request.post('/api/account/emails', {
            headers: authorized,
            data: {address: taken},
        })

        // Accepted rather than refused: saying "that one is taken" tells a stranger who has an
        // account here. What it must not do is give the address away.
        expect(claim.status()).toBe(202)
        const addresses = await (await request.get('/api/account/emails', {headers: authorized})).json()
        expect(addresses.some((a: {address: string}) => a.address === taken)).toBe(false)
    })

    test('claiming an address is shut to a visitor without a session', async ({request}) => {
        expect((await request.post('/api/account/emails',
            {data: {address: uniqueEmail('anon')}})).status()).toBe(401)
    })

    /**
     * There is no separate way to ask for the link again: claiming an address the account already
     * claims sends it once more, which is the same act.
     */
    test('claiming an address the account already claims sends the link again', async ({request}) => {
        const address = uniqueEmail('again')
        const signup = await request.post('/api/auth/signup', {data: {email: address, password: PASSWORD}})
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        expect((await request.post('/api/account/emails',
            {headers: authorized, data: {address}})).status()).toBe(202)

        const addresses = await (await request.get('/api/account/emails', {headers: authorized})).json()
        expect(addresses).toHaveLength(1)
    })

    test('a token nobody issued is refused', async ({request}) => {
        const response = await request.post('/api/auth/email/verify', {data: {token: '0'.repeat(64)}})

        expect(response.status()).toBe(400)
    })

    test('the verify page says so when the link is no good', async ({page}) => {
        await page.goto(`/verify-email?token=${'0'.repeat(64)}`)

        await expect(page.getByText(/expired or has already been used/i)).toBeVisible()
    })

    test('the security page lists the addresses and says which are confirmed', async ({page}) => {
        const account = await signUp(page, 'email-card')

        await page.goto('/account/security')

        // Scoped to the content: the status bar names the signed-in account as well.
        await expect(page.getByRole('main').getByText(account.email).first()).toBeVisible()
        await expect(page.getByText('not confirmed').first()).toBeVisible()
        await expect(page.getByText('written to').first()).toBeVisible()
        await expect(page.getByRole('button', {name: 'Add an address'})).toBeVisible()
    })
})
