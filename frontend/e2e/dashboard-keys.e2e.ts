/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {signUp} from './fixtures/auth'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'
import {sql} from './fixtures/database'
import {uniqueEmail} from './fixtures/unique'

/** The secret the stack's backend is given for the hosted dashboard gateway. */
const GATEWAY_SECRET = 'e2e-gateway-secret'

/**
 * A product of the story's own that hands out dashboard keys, so switching them on touches no product
 * another story reads.
 */
async function dashboardProduct(operator: APIRequestContext, prefix: string): Promise<{id: number, name: string}> {
    const name = uniqueName(prefix)
    const id = await createProduct(operator, name)
    const saved = await operator.put(`/api/admin/g/${GUILD}/products/${id}/dashboard-keys`, {
        data: {enabled: true, refreshDays: 7, expiryDays: 30, allowance: 10},
    })
    expect(saved.status()).toBe(200)
    return {id, name}
}

/** Gives an account a licence for the product, issued by the operator and redeemed by the account. */
async function entitle(operator: APIRequestContext, account: APIRequestContext, productId: number): Promise<void> {
    const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
        data: {productId, identifier: uniqueEmail('dashboard-buyer')},
    })).json()
    expect((await account.post('/api/account/licenses/redeem', {data: {key: issued.key}})).status()).toBe(201)
}

async function createKey(account: APIRequestContext, productId: number, name: string) {
    return account.post('/api/account/update-tokens', {data: {productId, name, kind: 'DASHBOARD'}})
}

/** Refreshes a key the way the plugin on a server does: no session, only the key. */
async function refresh(request: APIRequestContext, key: string) {
    return request.post('/api/v1/dashboard/key/refresh', {headers: {Authorization: `Bearer ${key}`}})
}

/** Verifies a key the way the hosted gateway does. */
async function verify(request: APIRequestContext, key: string, secret = GATEWAY_SECRET) {
    return request.post('/api/v1/dashboard/key/verify', {
        headers: {Authorization: `Bearer ${secret}`},
        data: {key},
    })
}

test.describe('A dashboard key on a server', () => {
    test('is verified by the gateway, refreshed on its schedule, kept in the grace, and expires unused', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard')
        const product = await dashboardProduct(operator, 'Dashboard')
        await entitle(operator, api, product.id)

        const created = await createKey(api, product.id, 'lobby')
        expect(created.status()).toBe(201)
        const key = await created.json()
        expect(key.token).toMatch(/^lyd_/)
        expect(key.kind).toBe('DASHBOARD')

        expect((await verify(request, key.token, 'not-the-secret')).status()).toBe(401)
        const verified = await verify(request, key.token)
        expect(verified.status()).toBe(200)
        const answer = await verified.json()
        expect(Object.keys(answer).sort()).toEqual(['keyId', 'kind', 'name', 'product', 'refreshAt', 'rotated', 'valid'])
        expect(answer).toMatchObject({valid: true, product: product.id, kind: 'BOUGHT', keyId: key.id, name: 'lobby', rotated: false})

        const early = await refresh(request, key.token)
        expect(early.status()).toBe(200)
        expect(await early.json()).toEqual({key: key.token, refreshAt: answer.refreshAt})

        sql(`UPDATE update_token SET rotated_at = rotated_at - interval '8 days', refreshed_at = NULL WHERE id = ${key.id}`)
        const due = await refresh(request, key.token)
        expect(due.status()).toBe(200)
        const next = (await due.json()).key as string
        expect(next).toMatch(/^lyd_/)
        expect(next).not.toBe(key.token)

        const lost = await refresh(request, key.token)
        expect(lost.status()).toBe(200)
        expect((await lost.json()).key).toBe(next)
        expect(await (await verify(request, key.token)).json()).toMatchObject({valid: true, keyId: key.id, rotated: true})

        sql(`UPDATE update_token_retired SET retired_at = retired_at - interval '11 minutes' WHERE update_token_id = ${key.id}`)
        const retired = await refresh(request, key.token)
        expect(retired.status()).toBe(401)
        expect(await retired.json()).toEqual({reason: 'RETIRED'})
        expect(await (await verify(request, key.token)).json()).toEqual({valid: false, reason: 'RETIRED'})
        const overview = await (await api.get('/api/account/update-tokens')).json()
        const entry = overview.tokens.find((token: {id: number}) => token.id === key.id)
        expect(entry.token).toBe(next)
        expect(entry.lastReuseAt).not.toBeNull()

        sql(`UPDATE update_token SET created_at = now() - interval '40 days', rotated_at = now() - interval '31 days',
             last_used_at = now() - interval '31 days', refreshed_at = NULL WHERE id = ${key.id}`)
        expect(await (await verify(request, next)).json()).toEqual({valid: false, reason: 'EXPIRED'})
        const expired = await refresh(request, next)
        expect(expired.status()).toBe(401)
        expect(await expired.json()).toEqual({reason: 'EXPIRED'})

        const reissued = await (await api.post(`/api/account/update-tokens/${key.id}/reissue`)).json()
        expect(reissued.token).toMatch(/^lyd_/)
        expect(await (await verify(request, reissued.token)).json()).toMatchObject({valid: true, keyId: key.id})
        await deleteProduct(operator, product.id, product.name)
    })

    test('a key nobody issued, an upgrade token, and a call without the secret are refused', async ({request}) => {
        const unknown = await refresh(request, 'lyd_nobody')
        expect(unknown.status()).toBe(401)
        expect(await unknown.json()).toEqual({reason: 'UNKNOWN'})
        expect(await (await verify(request, 'lyu_an_upgrade_token')).json()).toEqual({valid: false, reason: 'UNKNOWN'})
        expect((await request.post('/api/v1/dashboard/key/verify', {data: {key: 'lyd_nobody'}})).status()).toBe(401)
        expect((await request.post('/api/v1/dashboard/key/verify', {
            headers: {Authorization: `Bearer ${GATEWAY_SECRET}`},
            data: {},
        })).status()).toBe(400)
    })

    test('an account that lost the product is refused, and the gateway is told so', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard-lost')
        const product = await dashboardProduct(operator, 'Dashboard lost')
        await entitle(operator, api, product.id)
        const key = await (await createKey(api, product.id, 'lobby')).json()

        await operator.put(`/api/admin/g/${GUILD}/products/${product.id}/dashboard-keys`, {
            data: {enabled: false, refreshDays: 7, expiryDays: 30, allowance: 10},
        })

        const refused = await refresh(request, key.token)
        expect(refused.status()).toBe(403)
        expect(await refused.json()).toEqual({reason: 'NO_ACCESS'})
        expect(await (await verify(request, key.token)).json()).toEqual({valid: false, reason: 'NO_ACCESS'})
        await deleteProduct(operator, product.id, product.name)
    })
})

test.describe('A trial account', () => {
    test('holds one dashboard key, sees it as one of one, and cannot ask for more', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const account = await signUp(page, 'dashboard-trial')
        const api = await signedIn(account)
        const product = await dashboardProduct(operator, 'Dashboard trial')
        sql(`INSERT INTO trial (product_id, account_id)
             SELECT ${product.id}, account_id FROM account_email WHERE email = '${account.email}'`)

        const created = await createKey(api, product.id, 'home')
        expect(created.status()).toBe(201)
        const key = await created.json()
        const second = await createKey(api, product.id, 'second')
        expect(second.status()).toBe(409)
        expect(await second.text()).toBe('LIMIT_REACHED')
        const asked = await api.post('/api/account/update-tokens/requests', {
            data: {productId: product.id, amount: 1, reason: 'please', kind: 'DASHBOARD'},
        })
        expect(asked.status()).toBe(403)
        expect(await (await verify(request, key.token)).json()).toMatchObject({valid: true, kind: 'TRIAL'})

        await page.goto('/account/updaters')
        const section = page.getByRole('region', {name: 'Dashboard keys'}).getByRole('region', {name: product.name})
        await expect(section.getByText('1 of 1')).toBeVisible()
        await expect(section.getByRole('listitem', {name: 'home'}).getByText('trial', {exact: true})).toBeVisible()
        await expect(section.getByRole('button', {name: 'Request'})).toHaveCount(0)
        await expect(section.getByRole('button', {name: 'Add dashboard key'})).toHaveCount(0)
        await deleteProduct(operator, product.id, product.name)
    })
})

test.describe('The Server keys page', () => {
    test('a dashboard key is added by name and shown once with the command to paste', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const account = await signUp(page, 'dashboard-page')
        const product = await dashboardProduct(operator, 'Dashboard page')
        await entitle(operator, await signedIn(account), product.id)
        await page.goto('/account/updaters')

        const section = page.getByRole('region', {name: 'Dashboard keys'}).getByRole('region', {name: product.name})
        await expect(section.getByText('0 of 10')).toBeVisible()
        await section.getByPlaceholder('Name, e.g. the server it runs on').fill('lobby')
        await section.getByRole('button', {name: 'Add dashboard key'}).click()

        const command = section.getByRole('status').getByText(/^\/companyadmin dashboard key lyd_/)
        await expect(command).toBeVisible()
        const key = (await command.textContent())!.replace('/companyadmin dashboard key ', '').trim()
        expect(await (await verify(request, key)).json()).toMatchObject({valid: true, name: 'lobby'})
        const row = section.getByRole('listitem', {name: 'lobby'})
        await expect(row.getByText(/^next refresh/)).toBeVisible()
        await section.getByRole('button', {name: 'Done'}).click()
        await expect(section.getByRole('status')).toHaveCount(0)
        await deleteProduct(operator, product.id, product.name)
    })
})

test.describe('The admin area', () => {
    test('a product hands out dashboard keys once its form says so', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Dashboard form')
        const id = await createProduct(operator, name)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/products/${id}`)
        const form = page.getByRole('form', {name: 'Dashboard keys'})
        await form.getByLabel('Hand out dashboard keys').check()
        await form.getByLabel('Keys per buyer').fill('3')
        await form.getByRole('button', {name: 'Save dashboard keys'}).click()
        await expect(form.getByText('Saved')).toBeVisible()

        const settings = await (await operator.get(`/api/admin/g/${GUILD}/products/${id}/dashboard-keys`)).json()
        expect(settings).toEqual({enabled: true, refreshDays: 7, expiryDays: 30, allowance: 3})
        await deleteProduct(operator, id, name)
    })

    test('a guild admin sees who holds dashboard keys and revokes one', async ({page, request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard-revoked')
        const product = await dashboardProduct(operator, 'Dashboard admin')
        await entitle(operator, api, product.id)
        const key = await (await createKey(api, product.id, `lobby-${product.id}`)).json()
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/updaters`)
        await page.getByRole('tab', {name: 'Dashboard keys'}).click()
        const row = page.getByRole('region', {name: 'Dashboard keys held'}).getByRole('listitem').filter({hasText: key.name})
        await expect(row.getByText('1 held by this account')).toBeVisible()
        await row.getByRole('button', {name: 'Revoke'}).click()
        await row.getByRole('button', {name: 'Really revoke'}).click()
        await expect(row).toHaveCount(0)

        expect(await (await verify(request, key.token)).json()).toEqual({valid: false, reason: 'UNKNOWN'})
        await deleteProduct(operator, product.id, product.name)
    })

    test('requests for more dashboard keys are queued apart from upgrade tokens', async ({page, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard-cap')
        const product = await dashboardProduct(operator, 'Dashboard cap')
        await operator.put(`/api/admin/g/${GUILD}/products/${product.id}/dashboard-keys`, {
            data: {enabled: true, refreshDays: 7, expiryDays: 30, allowance: 1},
        })
        await entitle(operator, api, product.id)
        expect((await createKey(api, product.id, 'one')).status()).toBe(201)
        const reason = `a network ${product.id}`
        const asked = await api.post('/api/account/update-tokens/requests', {
            data: {productId: product.id, amount: 2, reason, kind: 'DASHBOARD'},
        })
        expect(asked.status()).toBe(201)
        const {account} = await asked.json()
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/updaters`)
        const requests = page.getByRole('region', {name: 'Requests for more'})
        await expect(requests.getByText(reason)).toHaveCount(0)
        await page.getByRole('tab', {name: 'Dashboard keys'}).click()
        const request = requests.getByRole('listitem', {name: account}).filter({hasText: reason})
        await request.getByRole('button', {name: 'Approve'}).click()
        await expect(request).toHaveCount(0)

        const overview = await (await api.get('/api/account/update-tokens')).json()
        const allowance = overview.products.find((entry: {productId: number, kind: string}) =>
            entry.productId === product.id && entry.kind === 'DASHBOARD')
        expect(allowance).toMatchObject({used: 1, allowed: 3, pending: false, trial: false})
        await deleteProduct(operator, product.id, product.name)
    })
})
