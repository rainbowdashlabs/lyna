/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {PASSWORD, signUp} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * Who may reach the instance area, and who may hand it out.
 *
 * <p>This stack names no operator in its configuration and grants none, so what these stories pin is
 * the floor: nobody gets in, and the endpoints that would grant it are shut to everybody. That is
 * the state a fresh instance is in, and the one a mistake here would quietly open up.
 */
test.describe('The operator endpoints', () => {
    test('are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.get('/api/admin/instance/operators')).status()).toBe(401)
        expect((await request.post('/api/admin/instance/operators',
            {data: {discordId: '123456789'}})).status()).toBe(401)
        expect((await request.delete('/api/admin/instance/operators/123456789')).status()).toBe(401)
    })

    test('are shut to somebody signed in who administers nothing', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('not-operator'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        expect((await request.get('/api/admin/instance/operators', {headers: authorized})).status()).toBe(404)
        expect((await request.post('/api/admin/instance/operators',
            {headers: authorized, data: {discordId: '123456789'}})).status()).toBe(404)
        expect((await request.delete('/api/admin/instance/operators/123456789',
            {headers: authorized})).status()).toBe(404)
    })

    test('an ordinary account cannot grant itself the instance', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('self-grant'), password: PASSWORD},
        })
        const {token} = await signup.json()

        const granted = await request.post('/api/admin/instance/operators', {
            headers: {Authorization: `Bearer ${token}`},
            data: {discordId: '4242424242'},
        })

        expect(granted.status()).toBe(404)
        // And the guild list it would have opened stays empty.
        const guilds = await request.get('/api/admin/guilds', {headers: {Authorization: `Bearer ${token}`}})
        expect(await guilds.json()).toEqual([])
    })
})

test.describe('The operators page', () => {
    test('sends an account that administers nothing back to its own area', async ({page}) => {
        await signUp(page, 'operators-page')

        await page.goto('/admin/instance/operators')

        await expect(page.getByText(/Operator access required/i)).toBeVisible()
    })
})
