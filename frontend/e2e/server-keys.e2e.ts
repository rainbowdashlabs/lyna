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

/** A key type as the admin endpoints answer it. */
interface KeyType {
    id: number
    slug: string
    name: string
    gatewaySecret: string
}

const DASHBOARD = {
    slug: 'dashboard', name: 'Dashboard key', command: '/companyadmin dashboard key {key}', enabled: true,
    refreshDays: 7, expiryDays: 30, allowance: 10, trialAllowance: 1,
}

/** Gives a product a key type, with the dashboard's settings unless told otherwise. */
async function addKeyType(
    operator: APIRequestContext, productId: number, overrides: Partial<typeof DASHBOARD> = {}): Promise<KeyType> {
    const created = await operator.post(`/api/admin/g/${GUILD}/products/${productId}/key-types`, {
        data: {...DASHBOARD, ...overrides},
    })
    expect(created.status()).toBe(201)
    return created.json()
}

/**
 * A product of the story's own with a dashboard key type, so setting it up touches no product another
 * story reads.
 */
async function dashboardProduct(
    operator: APIRequestContext, prefix: string, overrides: Partial<typeof DASHBOARD> = {},
): Promise<{id: number, name: string, type: KeyType}> {
    const name = uniqueName(prefix)
    const id = await createProduct(operator, name)
    return {id, name, type: await addKeyType(operator, id, overrides)}
}

/** Gives an account a licence for the product, issued by the operator and redeemed by the account. */
async function entitle(operator: APIRequestContext, account: APIRequestContext, productId: number): Promise<void> {
    const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
        data: {productId, identifier: uniqueEmail('dashboard-buyer')},
    })).json()
    expect((await account.post('/api/account/licenses/redeem', {data: {key: issued.key}})).status()).toBe(201)
}

async function createKey(account: APIRequestContext, product: {id: number, type: KeyType}, name: string) {
    return account.post('/api/account/update-tokens', {data: {productId: product.id, name, keyTypeId: product.type.id}})
}

/** Refreshes a key the way the plugin on a server does: no session, only the key. */
async function refresh(request: APIRequestContext, key: string) {
    return request.post('/api/v1/keys/refresh', {headers: {Authorization: `Bearer ${key}`}})
}

/** Verifies a key the way a key type's gateway does, with that type's secret. */
async function verify(request: APIRequestContext, key: string, secret: string) {
    return request.post('/api/v1/keys/verify', {
        headers: {Authorization: `Bearer ${secret}`},
        data: {key},
    })
}

test.describe('A server key on a server', () => {
    test('is verified by the gateway, refreshed on its schedule, kept in the grace, and expires unused', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard')
        const product = await dashboardProduct(operator, 'Dashboard')
        await entitle(operator, api, product.id)

        const secret = product.type.gatewaySecret
        const created = await createKey(api, product, 'lobby')
        expect(created.status()).toBe(201)
        const key = await created.json()
        expect(key.token).toMatch(/^lyd_/)
        expect(key.kind).toBe('SERVER')
        expect(key.keyTypeId).toBe(product.type.id)

        expect((await verify(request, key.token, 'lyg_not-the-secret')).status()).toBe(401)
        const verified = await verify(request, key.token, secret)
        expect(verified.status()).toBe(200)
        const answer = await verified.json()
        expect(Object.keys(answer).sort()).toEqual(['keyId', 'kind', 'name', 'product', 'refreshAt', 'rotated', 'type', 'valid'])
        expect(answer).toMatchObject({
            valid: true, product: product.id, type: 'dashboard', kind: 'BOUGHT', keyId: key.id, name: 'lobby', rotated: false,
        })

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
        expect(await (await verify(request, key.token, secret)).json()).toMatchObject({valid: true, keyId: key.id, rotated: true})

        sql(`UPDATE update_token_retired SET retired_at = retired_at - interval '11 minutes' WHERE update_token_id = ${key.id}`)
        const retired = await refresh(request, key.token)
        expect(retired.status()).toBe(401)
        expect(await retired.json()).toEqual({reason: 'RETIRED'})
        expect(await (await verify(request, key.token, secret)).json()).toEqual({valid: false, reason: 'RETIRED'})
        const overview = await (await api.get('/api/account/update-tokens')).json()
        const entry = overview.tokens.find((token: {id: number}) => token.id === key.id)
        expect(entry.token).toBe(next)
        expect(entry.lastReuseAt).not.toBeNull()

        sql(`UPDATE update_token SET created_at = now() - interval '40 days', rotated_at = now() - interval '31 days',
             last_used_at = now() - interval '31 days', refreshed_at = NULL WHERE id = ${key.id}`)
        expect(await (await verify(request, next, secret)).json()).toEqual({valid: false, reason: 'EXPIRED'})
        const expired = await refresh(request, next)
        expect(expired.status()).toBe(401)
        expect(await expired.json()).toEqual({reason: 'EXPIRED'})

        const reissued = await (await api.post(`/api/account/update-tokens/${key.id}/reissue`)).json()
        expect(reissued.token).toMatch(/^lyd_/)
        expect(await (await verify(request, reissued.token, secret)).json()).toMatchObject({valid: true, keyId: key.id})
        await deleteProduct(operator, product.id, product.name)
    })

    test('a key nobody issued, an upgrade token, and a call without a secret are refused', async ({request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const product = await dashboardProduct(operator, 'Refusing')
        const secret = product.type.gatewaySecret
        const unknown = await refresh(request, 'lyd_nobody')
        expect(unknown.status()).toBe(401)
        expect(await unknown.json()).toEqual({reason: 'UNKNOWN'})
        expect(await (await verify(request, 'lyu_an_upgrade_token', secret)).json()).toEqual({valid: false, reason: 'UNKNOWN'})
        expect((await request.post('/api/v1/keys/verify', {data: {key: 'lyd_nobody'}})).status()).toBe(401)
        expect((await request.post('/api/v1/keys/verify', {
            headers: {Authorization: `Bearer ${secret}`},
            data: {},
        })).status()).toBe(400)
        await deleteProduct(operator, product.id, product.name)
    })

    test('a key opens only its own key type\'s gateway, not another product\'s nor another type\'s', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('cross-type')
        const product = await dashboardProduct(operator, 'Own gateway')
        const other = await dashboardProduct(operator, 'Other gateway')
        const sibling = await addKeyType(operator, product.id, {slug: 'console', name: 'Console key'})
        await entitle(operator, api, product.id)
        await entitle(operator, api, other.id)
        const key = await (await createKey(api, product, 'lobby')).json()

        expect(await (await verify(request, key.token, product.type.gatewaySecret)).json()).toMatchObject({valid: true})
        expect(await (await verify(request, key.token, other.type.gatewaySecret)).json()).toEqual({valid: false, reason: 'UNKNOWN'})
        expect(await (await verify(request, key.token, sibling.gatewaySecret)).json()).toEqual({valid: false, reason: 'UNKNOWN'})

        const replaced = await (await operator.post(`/api/admin/g/${GUILD}/key-types/${product.type.id}/secret`)).json()
        expect((await verify(request, key.token, product.type.gatewaySecret)).status()).toBe(401)
        expect(await (await verify(request, key.token, replaced.gatewaySecret)).json()).toMatchObject({valid: true})
        await deleteProduct(operator, product.id, product.name)
        await deleteProduct(operator, other.id, other.name)
    })

    test('an account that lost the product is refused, and the gateway is told so', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard-lost')
        const product = await dashboardProduct(operator, 'Dashboard lost')
        await entitle(operator, api, product.id)
        const key = await (await createKey(api, product, 'lobby')).json()
        const secret = product.type.gatewaySecret

        await operator.put(`/api/admin/g/${GUILD}/key-types/${product.type.id}`, {data: {...DASHBOARD, enabled: false}})

        const refused = await refresh(request, key.token)
        expect(refused.status()).toBe(403)
        expect(await refused.json()).toEqual({reason: 'NO_ACCESS'})
        expect(await (await verify(request, key.token, secret)).json()).toEqual({valid: false, reason: 'NO_ACCESS'})
        await deleteProduct(operator, product.id, product.name)
    })
})

test.describe('A trial account', () => {
    test('holds the trial allowance of keys, sees it as one of one, and cannot ask for more', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const account = await signUp(page, 'dashboard-trial')
        const api = await signedIn(account)
        const product = await dashboardProduct(operator, 'Dashboard trial')
        sql(`INSERT INTO trial (product_id, account_id)
             SELECT ${product.id}, account_id FROM account_email WHERE email = '${account.email}'`)

        const created = await createKey(api, product, 'home')
        expect(created.status()).toBe(201)
        const key = await created.json()
        const second = await createKey(api, product, 'second')
        expect(second.status()).toBe(409)
        expect(await second.text()).toBe('LIMIT_REACHED')
        const asked = await api.post('/api/account/update-tokens/requests', {
            data: {productId: product.id, amount: 1, reason: 'please', keyTypeId: product.type.id},
        })
        expect(asked.status()).toBe(403)
        expect(await (await verify(request, key.token, product.type.gatewaySecret)).json()).toMatchObject({valid: true, kind: 'TRIAL'})

        await page.goto(`/products/${product.id}`)
        const section = page.getByTestId('product-server-keys').getByRole('region', {name: 'Dashboard key'})
        await expect(section.getByText('1 of 1')).toBeVisible()
        await expect(section.getByRole('listitem', {name: 'home'}).getByText('trial', {exact: true})).toBeVisible()
        await expect(section.getByRole('button', {name: 'Request'})).toHaveCount(0)
        await expect(section.getByRole('button', {name: 'Add key'})).toHaveCount(0)
        await deleteProduct(operator, product.id, product.name)
    })
})

test.describe('The product page', () => {
    test('a key is added by name and shown once in the product\'s command', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const account = await signUp(page, 'dashboard-page')
        const product = await dashboardProduct(operator, 'Dashboard page')
        await entitle(operator, await signedIn(account), product.id)
        await page.goto(`/products/${product.id}`)

        const section = page.getByTestId('product-server-keys').getByRole('region', {name: 'Dashboard key'})
        await expect(section.getByText('0 of 10')).toBeVisible()
        await section.getByPlaceholder('Name, e.g. the server it runs on').fill('lobby')
        await section.getByRole('button', {name: 'Add key'}).click()

        const command = section.getByRole('status').getByText(/^\/companyadmin dashboard key lyd_/)
        await expect(command).toBeVisible()
        const key = (await command.textContent())!.replace('/companyadmin dashboard key ', '').trim()
        expect(await (await verify(request, key, product.type.gatewaySecret)).json()).toMatchObject({valid: true, name: 'lobby'})
        const row = section.getByRole('listitem', {name: 'lobby'})
        await expect(row.getByText(/^next refresh/)).toBeVisible()
        await section.getByRole('button', {name: 'Done'}).click()
        await expect(section.getByRole('status')).toHaveCount(0)

        await page.goto('/account/updaters')
        await expect(page.getByTestId('product-key-links').getByRole('link', {name: `${product.name} · Dashboard key`}))
            .toHaveAttribute('href', `/products/${product.id}`)
        await deleteProduct(operator, product.id, product.name)
    })
})

test.describe('The admin area', () => {
    test('a key type is created on the product\'s Server keys tab and shows its gateway secret', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Key type form')
        const id = await createProduct(operator, name)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=keys`)
        const form = page.getByRole('form', {name: 'Add key type'})
        await form.getByTestId('key-type-name').fill('Dashboard key')
        await form.getByTestId('key-type-slug').fill('dashboard')
        await form.getByTestId('key-type-command').fill('/companyadmin dashboard key {key}')
        await form.getByRole('button', {name: 'Create key type'}).click()

        const card = page.getByRole('form', {name: 'Dashboard key'})
        await expect(card.getByTestId('key-type-secret')).toHaveText(/^lyg_/)
        const types = await (await operator.get(`/api/admin/g/${GUILD}/products/${id}/key-types`)).json()
        expect(types).toHaveLength(1)
        expect(types[0]).toMatchObject({slug: 'dashboard', name: 'Dashboard key', command: '/companyadmin dashboard key {key}', enabled: true})
        await deleteProduct(operator, id, name)
    })

    test('a guild admin sees who holds product keys and revokes one', async ({page, request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard-revoked')
        const product = await dashboardProduct(operator, 'Dashboard admin')
        await entitle(operator, api, product.id)
        const key = await (await createKey(api, product, `lobby-${product.id}`)).json()
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/updaters`)
        await page.getByRole('tab', {name: 'Product keys'}).click()
        const row = page.getByRole('region', {name: 'Product keys held'}).getByRole('listitem').filter({hasText: key.name})
        await expect(row.getByText('1 held by this account')).toBeVisible()
        await row.getByRole('button', {name: 'Revoke'}).click()
        await row.getByRole('button', {name: 'Really revoke'}).click()
        await expect(row).toHaveCount(0)

        expect(await (await verify(request, key.token, product.type.gatewaySecret)).json()).toEqual({valid: false, reason: 'UNKNOWN'})
        await deleteProduct(operator, product.id, product.name)
    })

    test('requests for more product keys are queued apart from upgrade tokens', async ({page, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('dashboard-cap')
        const product = await dashboardProduct(operator, 'Dashboard cap', {allowance: 1})
        await entitle(operator, api, product.id)
        expect((await createKey(api, product, 'one')).status()).toBe(201)
        const reason = `a network ${product.id}`
        const asked = await api.post('/api/account/update-tokens/requests', {
            data: {productId: product.id, amount: 2, reason, keyTypeId: product.type.id},
        })
        expect(asked.status()).toBe(201)
        const {account} = await asked.json()
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/updaters`)
        const requests = page.getByRole('region', {name: 'Requests for more'})
        await expect(requests.getByText(reason)).toHaveCount(0)
        await page.getByRole('tab', {name: 'Product keys'}).click()
        const request = requests.getByRole('listitem', {name: account}).filter({hasText: reason})
        await request.getByRole('button', {name: 'Approve'}).click()
        await expect(request).toHaveCount(0)

        const overview = await (await api.get('/api/account/update-tokens')).json()
        const allowance = overview.products.find((entry: {keyTypeId: number | null}) => entry.keyTypeId === product.type.id)
        expect(allowance).toMatchObject({used: 1, allowed: 3, pending: false, trial: false})
        await deleteProduct(operator, product.id, product.name)
    })
})
