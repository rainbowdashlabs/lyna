/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {PASSWORD, signUp} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * The per-product mail editor.
 *
 * <p>The stack administers no guild - that needs the gateway - so what these pin is the floor: the
 * endpoints that compose and send a mail are shut to everybody who is not an admin of the guild they
 * name. A mail is something the application sends under its own name, so who may write one matters
 * more than what they write.
 */
test.describe('The mailing endpoints', () => {
    const guild = '4242'

    test('are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.get(`/api/admin/g/${guild}/mailing`)).status()).toBe(401)
        expect((await request.put(`/api/admin/g/${guild}/mailing/1`,
            {data: {blocks: '[]'}})).status()).toBe(401)
        expect((await request.post(`/api/admin/g/${guild}/mailing/1/preview`,
            {data: {blocks: '[]'}})).status()).toBe(401)
        expect((await request.post(`/api/admin/g/${guild}/mailing/1/test`,
            {data: {address: uniqueEmail('test')}})).status()).toBe(401)
    })

    test('are shut to somebody signed in who administers nothing', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('not-admin'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}

        expect((await request.get(`/api/admin/g/${guild}/mailing`, {headers: authorized})).status()).toBe(404)
        expect((await request.put(`/api/admin/g/${guild}/mailing/1`,
            {headers: authorized, data: {blocks: '[]'}})).status()).toBe(404)
        expect((await request.post(`/api/admin/g/${guild}/mailing/1/preview`,
            {headers: authorized, data: {blocks: '[]'}})).status()).toBe(404)
    })

    test('sending a test mail is not a way to make the instance mail a stranger', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('would-spam'), password: PASSWORD},
        })
        const {token} = await signup.json()

        const sent = await request.post(`/api/admin/g/${guild}/mailing/1/test`, {
            headers: {Authorization: `Bearer ${token}`},
            data: {address: 'somebody-else@example.invalid'},
        })

        expect(sent.status()).toBe(404)
    })
})

test.describe('The mailing page', () => {
    test('is not reachable by an account that administers nothing', async ({page}) => {
        await signUp(page, 'mailing-page')

        await page.goto('/admin/g/4242/mailing')

        await expect(page.getByRole('heading', {level: 1, name: 'Mailing templates'})).toHaveCount(0)
    })
})
