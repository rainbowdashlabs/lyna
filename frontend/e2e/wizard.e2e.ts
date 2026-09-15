/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, request as playwrightRequest, test} from '@playwright/test'
import {PASSWORD} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * The four questions the download wizard asks, from the outside.
 *
 * <p>This stack runs without the bot, which the wizard no longer needs: what these stories are
 * about is who is refused, who is not, and with which answer - the part a visitor actually meets.
 */
const ENTITLED = {email: 'entitled@example.invalid', password: 'end-to-end-password'}

async function tokenFor(baseURL: string, account: {email: string; password: string}): Promise<string> {
    const context = await playwrightRequest.newContext({baseURL})
    try {
        const response = await context.post('/api/auth/login', {data: account})
        if (!response.ok()) throw new Error(`Login for ${account.email} answered ${response.status()}`)
        return (await response.json()).token
    } finally {
        await context.dispose()
    }
}

async function productId(request: {get: (url: string) => Promise<{json: () => Promise<unknown>}>}, name: string) {
    const products = await (await request.get('/api/v1/products')).json() as {id: number; name: string}[]
    const found = products.find(product => product.name === name)
    if (!found) throw new Error(`The seed did not create ${name}`)
    return found.id
}

test.describe('Step one, the release types', () => {
    test('a premium product is refused to a visitor who is not signed in', async ({request}) => {
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types`)

        expect(response.status()).toBe(401)
    })

    test('a premium product is refused to somebody signed in without a license', async ({request, baseURL}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('wizard-nolicense'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types`, {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.status()).toBe(403)
    })

    test('a license carries its holder past the entitlement check', async ({request, baseURL}) => {
        const token = await tokenFor(baseURL!, ENTITLED)
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types`, {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.ok()).toBe(true)
    })

    test('a free product asks nobody to sign in', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const response = await request.get(`/api/v1/products/${id}/release-types`)

        expect(response.ok()).toBe(true)
    })

    test('a product that does not exist is not found', async ({request}) => {
        const response = await request.get('/api/v1/products/999999/release-types')

        expect(response.status()).toBe(401)
    })
})

test.describe('Step two, the versions', () => {
    test('a release type nobody has heard of is not found', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const response = await request.get(`/api/v1/products/${id}/release-types/NONSENSE/versions`)

        expect(response.status()).toBe(404)
    })

    test('a release type the license does not cover is refused', async ({request, baseURL}) => {
        const token = await tokenFor(baseURL!, ENTITLED)
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types/DEV/versions`, {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.status()).toBe(403)
    })

    test('the release type the license does cover is allowed through', async ({request, baseURL}) => {
        const token = await tokenFor(baseURL!, ENTITLED)
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types/STABLE/versions`, {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.ok()).toBe(true)
    })
})

test.describe('Step four, the link', () => {
    test('issuing is refused to a visitor who is not signed in', async ({request}) => {
        const id = await productId(request, 'E2E Premium')

        const response = await request.post(`/api/v1/products/${id}/versions/1.0.0/downloads/1/issue`)

        expect(response.status()).toBe(401)
    })

    test('issuing is refused to somebody signed in without a license', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('wizard-issue'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const id = await productId(request, 'E2E Premium')

        const response = await request.post(`/api/v1/products/${id}/versions/1.0.0/downloads/1/issue`, {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.status()).toBe(403)
    })
})

test.describe('The wizard on the page', () => {
    test('a product with nothing published says so rather than failing', async ({page}) => {
        await page.goto('/')

        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()

        await expect(page.getByText(/Nothing is published for you to download yet/i)).toBeVisible()
    })

    test('the wizard closes again', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()
        await expect(page.getByText(/Nothing is published for you to download yet/i)).toBeVisible()

        await page.getByRole('button', {name: 'Close'}).click()

        await expect(page.getByText(/Nothing is published for you to download yet/i)).toHaveCount(0)
    })
})
