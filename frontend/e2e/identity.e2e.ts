/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {uniqueEmail} from './fixtures/unique'

/**
 * Being somebody: picking a name, and being named to the people you share a licence with.
 *
 * <p>The stack administers no guild - that needs the gateway - so no licence can be made here and
 * the sharing half is pinned at its floor: who the endpoints answer at all. The naming half is
 * reachable in full, and is where the rules actually live.
 */
test.describe('Account identity', () => {
    test('a new account has no name until it picks one', async ({signedUp}) => {
        const {api} = await signedUp('unnamed')

        const {account} = await (await api.get('/api/account')).json()

        expect(account.username).toBeNull()
        expect(account.nameIsTheirs).toBe(true)
    })

    test('picking a name adds four digits to it', async ({signedUp}) => {
        const {api} = await signedUp('picks-a-name')

        const response = await api.put('/api/account/username', {data: {username: 'ada'}})

        expect(response.ok()).toBe(true)
        const {username} = await response.json()
        expect(username).toMatch(/^ada#\d{4}$/)

        const {account} = await (await api.get('/api/account')).json()
        expect(account.username).toBe(username)
    })

    test('two people may both be ada, and are told apart by their digits', async ({signedUp}) => {
        const first = await signedUp('ada-one')
        const second = await signedUp('ada-two')

        const one = await (await first.api.put('/api/account/username',
            {data: {username: 'sharedname'}})).json()
        const two = await (await second.api.put('/api/account/username',
            {data: {username: 'sharedname'}})).json()

        expect(one.username).toMatch(/^sharedname#\d{4}$/)
        expect(two.username).toMatch(/^sharedname#\d{4}$/)
        expect(one.username).not.toBe(two.username)
    })

    test('a name nobody may take is refused rather than trimmed into something else', async ({signedUp}) => {
        const {api} = await signedUp('bad-name')

        for (const username of ['ab', 'a b', 'ada!', '.ada', '', 'a'.repeat(33)]) {
            const response = await api.put('/api/account/username', {data: {username}})
            expect(response.status(), `should have refused ${JSON.stringify(username)}`).toBe(400)
        }

        const {account} = await (await api.get('/api/account')).json()
        expect(account.username).toBeNull()
    })

    test('a name can be changed, and the old one is given up', async ({signedUp}) => {
        const {api} = await signedUp('renames')

        await api.put('/api/account/username', {data: {username: 'firstname'}})
        const second = await api.put('/api/account/username', {data: {username: 'secondname'}})

        expect(second.ok()).toBe(true)
        const {account} = await (await api.get('/api/account')).json()
        expect(account.username).toMatch(/^secondname#\d{4}$/)
    })

    test('naming an account needs a session', async ({request}) => {
        const response = await request.put('/api/account/username', {data: {username: 'nobody'}})

        expect(response.status()).toBe(401)
    })

    test('sharing a licence you do not hold finds nothing to share', async ({signedUp}) => {
        const {api} = await signedUp('not-an-owner')

        const share = await api.post('/api/account/licenses/1/sharees',
            {data: {subject: 'someone'}})
        const revoke = await api.delete('/api/account/licenses/1/sharees/a1')

        expect(share.status()).toBe(404)
        expect(revoke.status()).toBe(404)
    })

    test('the sharee endpoints are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.post('/api/account/licenses/1/sharees',
            {data: {subject: 'someone'}})).status()).toBe(401)
        expect((await request.delete('/api/account/licenses/1/sharees/a1')).status()).toBe(401)
    })

    test('an account lists the addresses it holds', async ({signedUp}) => {
        const {email, api} = await signedUp('one-address')

        const response = await api.get('/api/account/emails')

        expect(response.ok()).toBe(true)
        const addresses = await response.json()
        expect(addresses).toHaveLength(1)
        expect(addresses[0].address).toBe(email)
        expect(addresses[0].primary).toBe(true)
        expect(addresses[0].verified).toBe(false)
    })

    test('another address can be claimed, and arrives unproved', async ({signedUp}) => {
        const {api} = await signedUp('two-addresses')
        const second = uniqueEmail('second')

        expect((await api.post('/api/account/emails', {data: {address: second}})).status()).toBe(202)

        const addresses = await (await api.get('/api/account/emails')).json()
        expect(addresses).toHaveLength(2)
        expect(addresses.find((a: {address: string}) => a.address === second).verified).toBe(false)
    })

    /**
     * The address an account is written to is where a password reset is sent, so it cannot be given
     * up and cannot be replaced by one nobody has proved.
     */
    test('the address the account is written to is held down', async ({signedUp}) => {
        const {email, api} = await signedUp('primary-held')
        const second = uniqueEmail('cannot-be-primary')
        await api.post('/api/account/emails', {data: {address: second}})

        const removePrimary = await api.delete(`/api/account/emails/${encodeURIComponent(email)}`)
        const promoteUnproved = await api.post(`/api/account/emails/${encodeURIComponent(second)}/primary`)

        expect(removePrimary.status()).toBe(409)
        expect(promoteUnproved.status()).toBe(409)
    })

    test('a claimed address can be given up again', async ({signedUp}) => {
        const {api} = await signedUp('gives-up')
        const second = uniqueEmail('given-up')
        await api.post('/api/account/emails', {data: {address: second}})

        expect((await api.delete(`/api/account/emails/${encodeURIComponent(second)}`)).status()).toBe(204)

        expect(await (await api.get('/api/account/emails')).json()).toHaveLength(1)
    })

    test('the addresses are shut to a visitor who is not signed in', async ({request}) => {
        expect((await request.get('/api/account/emails')).status()).toBe(401)
        expect((await request.post('/api/account/emails', {data: {address: 'x@example.invalid'}})).status()).toBe(401)
    })
})