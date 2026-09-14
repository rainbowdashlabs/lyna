/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {signUp} from './fixtures/auth'

/**
 * The storefront against the catalogue the `e2e` compose profile seeds: one free product, one
 * premium product with somewhere to buy it, and one premium product with nowhere. Those are the
 * three answers a tile can give, which is what the stories here are about.
 */
test.describe('The storefront', () => {
    test('greets a visitor who is not signed in', async ({page}) => {
        await page.goto('/')

        await expect(page.getByRole('heading', {level: 1, name: 'Lyna Download Center'})).toBeVisible()
    })

    test('a free product offers a download to anyone', async ({page}) => {
        await page.goto('/')

        const tile = page.getByRole('article').filter({hasText: 'E2E Freebie'})
        await expect(tile.getByRole('button', {name: 'Download'})).toBeVisible()
        await expect(tile.getByText('Free', {exact: true})).toBeVisible()
    })

    test('a premium product offers a purchase rather than a download', async ({page}) => {
        await page.goto('/')

        const tile = page.getByRole('article').filter({hasText: 'E2E Premium'})
        await expect(tile.getByRole('link', {name: /Buy on Ko-fi/})).toHaveAttribute(
            'href', 'https://ko-fi.com/s/e2eshop')
        await expect(tile.getByRole('button', {name: 'Download'})).toHaveCount(0)
    })

    test('a premium product with nowhere to buy it says so instead of offering a dead button', async ({page}) => {
        await page.goto('/')

        const tile = page.getByRole('article').filter({hasText: 'E2E Unsellable'})
        await expect(tile.getByText('Not for sale here')).toBeVisible()
        await expect(tile.getByRole('link', {name: /Buy on Ko-fi/})).toHaveCount(0)
    })

    test('a signed-in visitor holding no license is pointed at linking their Discord', async ({page}) => {
        await signUp(page, 'kiosk-hint')

        await page.goto('/')

        const tile = page.getByRole('article').filter({hasText: 'E2E Premium'})
        await expect(tile.getByRole('link', {name: 'Link your Discord'})).toBeVisible()
    })

    test('the Free filter leaves the premium products out', async ({page}) => {
        await page.goto('/')

        await page.getByRole('button', {name: 'Free'}).click()

        await expect(page.getByRole('article').filter({hasText: 'E2E Freebie'})).toBeVisible()
        await expect(page.getByRole('article').filter({hasText: 'E2E Premium'})).toHaveCount(0)
    })

    test('search narrows to what was typed, and clearing brings the rest back', async ({page}) => {
        await page.goto('/')

        await page.getByRole('searchbox').fill('Unsellable')
        await expect(page.getByRole('article')).toHaveCount(1)

        await page.getByRole('searchbox').fill('')
        await expect(page.getByRole('article')).toHaveCount(3)
    })

    test('a search matching nothing explains itself and offers a way back', async ({page}) => {
        await page.goto('/')

        await page.getByRole('searchbox').fill('nothing matches this')

        await expect(page.getByText('No plugins match.')).toBeVisible()
        await page.getByRole('button', {name: 'Clear filters'}).click()
        await expect(page.getByRole('article')).toHaveCount(3)
    })

    test('Owned appears only once somebody is signed in', async ({page}) => {
        await page.goto('/')
        await expect(page.getByRole('button', {name: 'Owned'})).toHaveCount(0)

        await signUp(page, 'kiosk-owned')
        await page.goto('/')

        await expect(page.getByRole('button', {name: 'Owned'})).toBeVisible()
    })

    test('Owned is empty for somebody holding no license', async ({page}) => {
        await signUp(page, 'kiosk-owned-empty')
        await page.goto('/')

        await page.getByRole('button', {name: 'Owned'}).click()

        await expect(page.getByText('No plugins match.')).toBeVisible()
    })
})

test.describe('The catalogue endpoint', () => {
    test('is served to anyone, premium products included', async ({request}) => {
        const response = await request.get('/api/v1/products')

        expect(response.ok()).toBe(true)
        const products = await response.json() as {name: string; free: boolean}[]
        expect(products.map(product => product.name)).toContain('E2E Premium')
    })

    test('entitles nobody who is not signed in', async ({request}) => {
        const products = await (await request.get('/api/v1/products')).json() as {entitled: boolean}[]

        expect(products.every(product => !product.entitled)).toBe(true)
    })

    test('a premium product refuses its releases to a visitor who is not signed in', async ({request}) => {
        const products = await (await request.get('/api/v1/products')).json() as {id: number; name: string}[]
        const premium = products.find(product => product.name === 'E2E Premium')!

        const response = await request.get(`/api/v1/releases/${premium.id}`)

        expect(response.status()).toBe(401)
    })

    /**
     * This stack runs without the bot, and resolving a product's downloads is the one part of the
     * API that needs it. What matters is that it says so rather than failing as though broken.
     */
    test('a free product says the gateway is needed rather than failing as though broken', async ({request}) => {
        const products = await (await request.get('/api/v1/products')).json() as {id: number; name: string}[]
        const free = products.find(product => product.name === 'E2E Freebie')!

        const response = await request.get(`/api/v1/releases/${free.id}`)

        expect(response.status()).toBe(503)
    })

    test('a premium product refuses its releases to somebody holding no license', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: `kiosk-forbidden-${Date.now()}@example.invalid`, password: 'end-to-end-password'},
        })
        const {token} = await signup.json()
        const products = await (await request.get('/api/v1/products')).json() as {id: number; name: string}[]
        const premium = products.find(product => product.name === 'E2E Premium')!

        const response = await request.get(`/api/v1/releases/${premium.id}`, {
            headers: {Authorization: `Bearer ${token}`},
        })

        expect(response.status()).toBe(403)
    })
})
