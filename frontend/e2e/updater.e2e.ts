/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'

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
