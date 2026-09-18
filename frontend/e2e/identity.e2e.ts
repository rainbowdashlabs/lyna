/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {PASSWORD} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * Being somebody: picking a name, and being named to the people you share a licence with.
 *
 * <p>The stack administers no guild - that needs the gateway - so no licence can be made here and
 * the sharing half is pinned at its floor: who the endpoints answer at all. The naming half is
 * reachable in full, and is where the rules actually live.
 */
test.describe('Account identity', () => {
    async function signUpFor(request: import('@playwright/test').APIRequestContext, prefix: string) {
        const email = uniqueEmail(prefix)
        const signup = await request.post('/api/auth/signup', {data: {email, password: PASSWORD}})
        expect(signup.ok()).toBe(true)
        const {token} = await signup.json()
        return {email, headers: {Authorization: `Bearer ${token}`}}
    }

    test('a new account has no name until it picks one', async ({request}) => {
        const {headers} = await signUpFor(request, 'unnamed')

        const {account} = await (await request.get('/api/account', {headers})).json()

        expect(account.username).toBeNull()
        expect(account.nameIsTheirs).toBe(true)
    })

    test('picking a name adds four digits to it', async ({request}) => {
        const {headers} = await signUpFor(request, 'picks-a-name')

        const response = await request.put('/api/account/username', {headers, data: {username: 'ada'}})

        expect(response.ok()).toBe(true)
        const {username} = await response.json()
        expect(username).toMatch(/^ada#\d{4}$/)

        const {account} = await (await request.get('/api/account', {headers})).json()
        expect(account.username).toBe(username)
    })

    test('two people may both be ada, and are told apart by their digits', async ({request}) => {
        const first = await signUpFor(request, 'ada-one')
        const second = await signUpFor(request, 'ada-two')

        const one = await (await request.put('/api/account/username',
            {headers: first.headers, data: {username: 'sharedname'}})).json()
        const two = await (await request.put('/api/account/username',
            {headers: second.headers, data: {username: 'sharedname'}})).json()

        expect(one.username).toMatch(/^sharedname#\d{4}$/)
        expect(two.username).toMatch(/^sharedname#\d{4}$/)
        expect(one.username).not.toBe(two.username)
    })

    test('a name nobody may take is refused rather than trimmed into something else', async ({request}) => {
        const {headers} = await signUpFor(request, 'bad-name')

        for (const username of ['ab', 'a b', 'ada!', '.ada', '', 'a'.repeat(33)]) {
            const response = await request.put('/api/account/username', {headers, data: {username}})
            expect(response.status(), `should have refused ${JSON.stringify(username)}`).toBe(400)
        }

        const {account} = await (await request.get('/api/account', {headers})).json()
        expect(account.username).toBeNull()
    })

    test('a name can be changed, and the old one is given up', async ({request}) => {
        const {headers} = await signUpFor(request, 'renames')

        await request.put('/api/account/username', {headers, data: {username: 'firstname'}})
        const second = await request.put('/api/account/username', {headers, data: {username: 'secondname'}})

        expect(second.ok()).toBe(true)
        const {account} = await (await request.get('/api/account', {headers})).json()
        expect(account.username).toMatch(/^secondname#\d{4}$/)
    })

    test('naming an account needs a session', async ({request}) => {
        const response = await request.put('/api/account/username', {data: {username: 'nobody'}})

        expect(response.status()).toBe(401)
    })

    test('sharing a licence you do not hold finds nothing to share', async ({request}) => {
        const {headers} = await signUpFor(request, 'not-an-owner')

        const share = await request.post('/api/account/licenses/1/sharees',
            {headers, data: {subject: 'someone'}})
        const revoke = await request.delete('/api/account/licenses/1/sharees/a1', {headers})

        expect(share.status()).toBe(404)
        expect(revoke.status()).toBe(404)
    })

    test('the sharee endpoints are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.post('/api/account/licenses/1/sharees',
            {data: {subject: 'someone'}})).status()).toBe(401)
        expect((await request.delete('/api/account/licenses/1/sharees/a1')).status()).toBe(401)
    })

    test('an account lists the addresses it holds', async ({request}) => {
        const {email, headers} = await signUpFor(request, 'one-address')

        const response = await request.get('/api/account/emails', {headers})

        expect(response.ok()).toBe(true)
        const addresses = await response.json()
        expect(addresses).toHaveLength(1)
        expect(addresses[0].address).toBe(email)
        expect(addresses[0].primary).toBe(true)
        expect(addresses[0].verified).toBe(false)
    })

    test('another address can be claimed, and arrives unproved', async ({request}) => {
        const {headers} = await signUpFor(request, 'two-addresses')
        const second = uniqueEmail('second')

        expect((await request.post('/api/account/emails', {headers, data: {address: second}})).status()).toBe(202)

        const addresses = await (await request.get('/api/account/emails', {headers})).json()
        expect(addresses).toHaveLength(2)
        expect(addresses.find((a: {address: string}) => a.address === second).verified).toBe(false)
    })

    /**
     * The address an account is written to is where a password reset is sent, so it cannot be given
     * up and cannot be replaced by one nobody has proved.
     */
    test('the address the account is written to is held down', async ({request}) => {
        const {email, headers} = await signUpFor(request, 'primary-held')
        const second = uniqueEmail('cannot-be-primary')
        await request.post('/api/account/emails', {headers, data: {address: second}})

        const removePrimary = await request.delete(`/api/account/emails/${encodeURIComponent(email)}`, {headers})
        const promoteUnproved = await request.post(
            `/api/account/emails/${encodeURIComponent(second)}/primary`, {headers})

        expect(removePrimary.status()).toBe(409)
        expect(promoteUnproved.status()).toBe(409)
    })

    test('a claimed address can be given up again', async ({request}) => {
        const {headers} = await signUpFor(request, 'gives-up')
        const second = uniqueEmail('given-up')
        await request.post('/api/account/emails', {headers, data: {address: second}})

        expect((await request.delete(`/api/account/emails/${encodeURIComponent(second)}`, {headers})).status()).toBe(204)

        expect(await (await request.get('/api/account/emails', {headers})).json()).toHaveLength(1)
    })

    test('the addresses are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.get('/api/account/emails')).status()).toBe(401)
        expect((await request.post('/api/account/emails', {data: {address: 'x@example.invalid'}})).status()).toBe(401)
    })
})