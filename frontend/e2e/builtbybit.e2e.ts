/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/** The secrets `docker/config-e2e/config.yaml` gives the e2e backend. */
const WEBHOOK_SECRET = 'e2e-builtbybit-webhook-secret'
const KEY_SECRET = 'e2e-builtbybit-key-secret'

/** A BuiltByBit id no other story or run uses. */
function uniqueId(): number {
    return Date.now() * 100 + Math.floor(Math.random() * 100)
}

/** Sells a story's product as a BuiltByBit resource, through the product editor's endpoint. */
async function sellOnBuiltByBit(operator: APIRequestContext, id: number, name: string, resourceId: number): Promise<void> {
    const saved = await operator.put(`/api/admin/g/${GUILD}/products/${id}`, {
        data: {
            name, url: null, roleId: '99', free: false, trial: false, iconUrl: null, butlerId: null,
            builtbybitResourceId: resourceId, builtbybitAddonId: null, wikiUrl: null, reviewUrl: null, parentId: null,
        },
    })
    expect(saved.status()).toBe(204)
}

function purchase(memberId: number, resourceId: number, bundleId = ''): string {
    return JSON.stringify({
        username: 'Ada', user_id: `${memberId}`, user_url: '', resource_title: 'Widget', resource_id: `${resourceId}`,
        resource_url: '', addon_id: '', addon_title: '', bundle_id: bundleId, bundle_title: '', renewal: '',
        list_price: '10.00', final_price: '10.00', purchase_date: '1700000000',
    })
}

function keyRequest(memberId: number, resourceId: number, secret = KEY_SECRET): Record<string, string> {
    return {
        builtbybit: 'true', steam_id: '', user_id: `${memberId}`, resource_id: `${resourceId}`,
        version_id: '1', version_number: '1.0.0', secret,
    }
}

test.describe('BuiltByBit', () => {
    test('a purchase webhook at the wrong address is refused', async ({request}) => {
        const response = await request.post('/api/v1/builtbybit/not-the-secret', {data: purchase(1, 1)})

        expect(response.status()).toBe(403)
    })

    test('a key request without the right secret is refused', async ({request}) => {
        const response = await request.post('/api/v1/builtbybit/key', {form: keyRequest(1, 1, 'guess')})

        expect(response.status()).toBe(403)
    })

    test('a purchase and the downloads after it meet at one license with one key', async ({request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Sold on BuiltByBit')
        const id = await createProduct(operator, name)
        const resourceId = uniqueId()
        const memberId = uniqueId()
        await sellOnBuiltByBit(operator, id, name, resourceId)

        const bought = await request.post(`/api/v1/builtbybit/${WEBHOOK_SECRET}`, {data: purchase(memberId, resourceId)})
        expect(bought.status()).toBe(200)

        const first = await request.post('/api/v1/builtbybit/key', {form: keyRequest(memberId, resourceId)})
        expect(first.status()).toBe(200)
        expect(first.headers()['content-type']).toContain('text/plain')
        const key = await first.text()
        const second = await request.post('/api/v1/builtbybit/key', {form: keyRequest(memberId, resourceId)})
        expect(await second.text()).toBe(key)

        const licenses = await (await operator.get(`/api/admin/g/${GUILD}/licenses`)).json()
        const issued = licenses.filter((license: {identifier: string, productId: number}) =>
            license.identifier === `${memberId}` && license.productId === id)
        expect(issued).toHaveLength(1)
        await deleteProduct(operator, id, name)
    })

    test('a key request for a resource Lyna does not sell answers no key', async ({request}) => {
        const response = await request.post('/api/v1/builtbybit/key', {form: keyRequest(uniqueId(), uniqueId())})

        expect(response.status()).toBe(404)
        expect(await response.text()).toBe('')
    })

    test('the admin page lists a bundle purchase as not issued', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Bundled')
        const id = await createProduct(operator, name)
        const resourceId = uniqueId()
        await sellOnBuiltByBit(operator, id, name, resourceId)
        await request.post(`/api/v1/builtbybit/${WEBHOOK_SECRET}`, {data: purchase(uniqueId(), resourceId, '5')})
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/builtbybit`)

        await expect(page.getByTestId('builtbybit-key-url')).toContainText('/api/v1/builtbybit/key')
        await expect(page.getByTestId('builtbybit-calls').getByText(name).first()).toBeVisible()
        await expect(page.getByTestId('builtbybit-calls').getByRole('group').filter({hasText: name}).first())
            .toContainText('Bundle, not issued')
        await deleteProduct(operator, id, name)
    })
})

test.describe('The buy button', () => {
    test('a product sold on BuiltByBit only is bought there directly', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('BuiltByBit only')
        const id = await createProduct(operator, name)
        const resourceId = uniqueId()
        await sellOnBuiltByBit(operator, id, name, resourceId)

        await page.goto(`/products/${id}`)

        await expect(page.getByRole('link', {name: 'Buy on BuiltByBit'}))
            .toHaveAttribute('href', `https://builtbybit.com/resources/${resourceId}/`)
        await deleteProduct(operator, id, name)
    })

    test('a product sold on two platforms lets the buyer pick one', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Sold twice')
        const id = await createProduct(operator, name)
        const resourceId = uniqueId()
        const code = `e2e${Date.now()}`
        await operator.post(`/api/admin/g/${GUILD}/kofi`, {data: {linkCode: code, productId: id}})
        await sellOnBuiltByBit(operator, id, name, resourceId)

        await page.goto(`/products/${id}`)
        await page.getByRole('button', {name: 'Buy'}).click()

        const picker = page.getByTestId('buy-picker')
        await expect(picker.getByRole('link', {name: 'Buy on Ko-fi'})).toHaveAttribute('href', `https://ko-fi.com/s/${code}`)
        await expect(picker.getByRole('link', {name: 'Buy on BuiltByBit'}))
            .toHaveAttribute('href', `https://builtbybit.com/resources/${resourceId}/`)
        await deleteProduct(operator, id, name)
    })
})
