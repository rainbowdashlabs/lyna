/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {signUp} from './fixtures/auth'
import {GUILD, OPERATOR} from './fixtures/admin'
import {uniqueEmail} from './fixtures/unique'

/**
 * The id the seed gave a product, looked up by name.
 */
async function productId(request: APIRequestContext, name: string) {
    const products = await (await request.get('/api/v1/products')).json() as {id: number; name: string}[]
    const found = products.find(product => product.name === name)
    if (!found) throw new Error(`The seed did not create ${name}`)
    return found.id
}

/**
 * An updater running on somebody's server, with no browser and no session.
 */
test.describe('Updating a free product', () => {
    test('the check names where the new build is, and that address serves it', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const check = await (await request.get(`/api/v1/update/check?id=${id}&version=1.0.0`)).json()

        expect(check.update).toBe(true)
        expect(check.latest).toBe('1.1.0')
        const file = await request.get(check.download)
        expect(file.ok()).toBe(true)
        expect(file.headers()['content-disposition']).toContain('E2EFreebie-1.1.0.jar')
    })

    test('a check that finds nothing newer names no download', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const check = await (await request.get(`/api/v1/update/check?id=${id}&version=1.1.0`)).json()

        expect(check.update).toBe(false)
        expect(check.download).toBeNull()
    })

    test('the newest build can be fetched without asking which it is', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')
        const versions = await (await request.get(`/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {downloadTypeIds: number[]}[]

        const file = await request.get(`/api/v1/download/direct/${id}/${versions[0]!.downloadTypeIds[0]}/latest`)

        expect(file.ok()).toBe(true)
        expect(file.headers()['content-disposition']).toContain('E2EFreebie-1.1.0.jar')
    })

    test('a premium check names no download', async ({request}) => {
        const id = await productId(request, 'E2E Premium')

        const check = await (await request.get(`/api/v1/update/check?id=${id}&version=1.0.0`)).json()

        expect(check.update).toBe(true)
        expect(check.download).toBeNull()
    })
})

/**
 * Gives an account a licence for the seeded premium product, the way somebody who bought it would
 * have one: an operator issues it and the account redeems the key.
 */
async function entitle(operator: APIRequestContext, account: APIRequestContext): Promise<number> {
    const id = await productId(account, 'E2E Premium')
    const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
        data: {productId: id, identifier: uniqueEmail('updater-buyer')},
    })).json()
    const redeemed = await account.post('/api/account/licenses/redeem', {data: {key: issued.key}})
    expect(redeemed.status()).toBe(201)
    return id
}

/** Trades an upgrade token the way an updater on a server does: no session, only the token. */
async function trade(request: APIRequestContext, token: string, body?: object) {
    return request.post('/api/v1/update/token', {
        headers: {Authorization: `Bearer ${token}`},
        ...(body ? {data: body} : {}),
    })
}

test.describe('Updating a premium product', () => {
    test('a token buys one download link, which serves the build more than once, and the next token', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('updater')
        const id = await entitle(operator, api)
        const created = await (await api.post('/api/account/update-tokens', {data: {productId: id, name: 'lobby'}})).json()
        expect(created.token).toMatch(/^lyu_/)

        const response = await trade(request, created.token)

        expect(response.status()).toBe(201)
        const exchanged = await response.json()
        expect(exchanged.upgradeToken).not.toBe(created.token)
        expect(exchanged.version).toBe('1.1.0')
        expect((await request.get(exchanged.downloadUrl)).ok()).toBe(true)
        expect((await request.get(exchanged.downloadUrl)).ok()).toBe(true)

        const again = await trade(request, created.token)
        expect(again.status()).toBe(401)
        expect(await again.text()).toBe('REUSED')

        const overview = await (await api.get('/api/account/update-tokens')).json()
        const entry = overview.tokens.find((token: {id: number}) => token.id === created.id)
        expect(entry.token).toBe(exchanged.upgradeToken)
        expect(entry.lastUsedAt).not.toBeNull()
        expect(entry.lastReuseAt).not.toBeNull()
        const history = await (await api.get('/api/account/downloads')).json() as {rows: {source: string}[]}
        expect(history.rows[0]!.source).toBe('updater')
    })

    test('a build that does not exist costs nothing, and the token still trades afterwards', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('updater-miss')
        const id = await entitle(operator, api)
        const {token} = await (await api.post('/api/account/update-tokens', {data: {productId: id, name: 'lobby'}})).json()

        expect((await trade(request, token, {version: '9.9.9'})).status()).toBe(404)

        const traded = await trade(request, token, {version: '1.0.0'})
        expect(traded.status()).toBe(201)
        expect((await traded.json()).version).toBe('1.0.0')
    })

    test('a new token has to wait a moment before it trades again', async ({request, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const {api} = await signedUp('updater-soon')
        const id = await entitle(operator, api)
        const {token} = await (await api.post('/api/account/update-tokens', {data: {productId: id, name: 'lobby'}})).json()
        const {upgradeToken} = await (await trade(request, token)).json()

        const tooSoon = await trade(request, upgradeToken)

        expect(tooSoon.status()).toBe(429)
    })

    test('a token nobody issued is refused', async ({request}) => {
        const response = await trade(request, 'lyu_nobody')

        expect(response.status()).toBe(401)
        expect(await response.text()).toBe('UNKNOWN')
    })

    test('an account without the product gets no token', async ({signedUp}) => {
        const {api} = await signedUp('updater-none')
        const id = await productId(api, 'E2E Premium')

        const refused = await api.post('/api/account/update-tokens', {data: {productId: id, name: 'lobby'}})

        expect(refused.status()).toBe(403)
    })
})

test.describe('The updaters page', () => {
    test('an updater is added by name, its token shown on asking, and reissued after a second click', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const account = await signUp(page, 'updater-page')
        await entitle(operator, await signedIn(account))
        await page.goto('/account/updaters')

        const product = page.getByRole('region', {name: 'E2E Premium'})
        await expect(product.getByText('0 / 10')).toBeVisible()
        await product.getByPlaceholder('Name, e.g. the server it runs on').fill('lobby')
        await product.getByRole('button', {name: 'Add updater'}).click()

        const row = product.getByRole('listitem', {name: 'lobby'})
        await expect(row).toBeVisible()
        await expect(product.getByText('1 / 10')).toBeVisible()
        await expect(row.getByText('never used')).toBeVisible()
        await row.getByRole('button', {name: 'Show'}).click()
        const token = await row.getByText(/^lyu_/).textContent()

        await row.getByRole('button', {name: 'Reissue'}).click()
        await row.getByRole('button', {name: 'Really reissue'}).click()

        await expect(row.getByText(token!)).toHaveCount(0)
    })
})
