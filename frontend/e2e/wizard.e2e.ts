/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, request as playwrightRequest, test} from '@playwright/test'
import {PASSWORD} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/**
 * The four questions the download wizard asks, from the outside.
 *
 * <p>The stack serves a catalogue of its own and a Nexus of its own, so these walk the whole way:
 * which release types, which versions, and then the file itself.
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

/**
 * Which release types and versions exist is open to anybody - somebody deciding whether to buy learns
 * the plugin is maintained. Whether they may download is said alongside; the link itself is guarded.
 */
test.describe('Step one, the release types', () => {
    async function releaseTypes(request: APIRequestContext, name: string, token?: string) {
        const id = await productId(request, name)
        const response = await request.get(`/api/v1/products/${id}/release-types`, {
            headers: token ? {Authorization: `Bearer ${token}`} : {},
        })
        expect(response.ok()).toBe(true)
        return await response.json() as {id: string; downloadable: boolean}[]
    }

    test('a premium product shows a visitor what exists, and that they cannot download it', async ({request}) => {
        expect(await releaseTypes(request, 'E2E Premium')).toEqual([expect.objectContaining({id: 'STABLE', downloadable: false})])
    })

    test('signing in without a license changes nothing about that', async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('wizard-nolicense'), password: PASSWORD},
        })
        const {token} = await signup.json()

        expect(await releaseTypes(request, 'E2E Premium', token)).toEqual([expect.objectContaining({downloadable: false})])
    })

    test('a license makes the release type it covers downloadable', async ({request, baseURL}) => {
        const token = await tokenFor(baseURL!, ENTITLED)

        expect(await releaseTypes(request, 'E2E Premium', token)).toEqual([expect.objectContaining({id: 'STABLE', downloadable: true})])
    })

    test('a free product is downloadable by anybody', async ({request}) => {
        expect(await releaseTypes(request, 'E2E Freebie')).toEqual([expect.objectContaining({downloadable: true})])
    })

    test('a product that does not exist is not found', async ({request}) => {
        const response = await request.get('/api/v1/products/999999/release-types')

        expect(response.status()).toBe(404)
    })
})

test.describe('Step two, the versions', () => {
    test('a release type nobody has heard of is not found', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const response = await request.get(`/api/v1/products/${id}/release-types/NONSENSE/versions`)

        expect(response.status()).toBe(404)
    })

    test('a premium product lists its versions to a visitor, newest first', async ({request}) => {
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types/STABLE/versions`)

        expect(response.ok()).toBe(true)
        const versions = await response.json() as {version: string}[]
        expect(versions.map(entry => entry.version)).toEqual(['1.1.0', '1.0.0'])
    })

    test('a release type without builds lists nothing rather than refusing', async ({request}) => {
        const id = await productId(request, 'E2E Premium')

        const response = await request.get(`/api/v1/products/${id}/release-types/DEV/versions`)

        expect(response.ok()).toBe(true)
        expect(await response.json()).toEqual([])
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
    test('opens straight on the versions, because there is only one release type to pick', async ({page}) => {
        await page.goto('/')

        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()

        await expect(page.getByRole('button', {name: /1\.1\.0/})).toBeVisible()
        await expect(page.getByRole('button', {name: /1\.0\.0/})).toBeVisible()
    })

    test('picking a version offers the file, named and sized', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()

        await page.getByRole('button', {name: /1\.1\.0/}).click()

        await expect(page.getByText('E2EFreebie-1.1.0.jar')).toBeVisible()
        await expect(page.getByText(/one-time link/i)).toBeVisible()
    })

    test('the download type step is skipped, because the version has only one', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()

        await page.getByRole('button', {name: /1\.1\.0/}).click()

        await expect(page.getByText('E2EFreebie-1.1.0.jar')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Jar', exact: true})).toHaveCount(0)
    })

    test('back returns to the versions, and closing ends it', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()
        await page.getByRole('button', {name: /1\.1\.0/}).click()
        await expect(page.getByText('E2EFreebie-1.1.0.jar')).toBeVisible()

        await page.getByRole('button', {name: 'Back'}).click()
        await expect(page.getByRole('button', {name: /1\.0\.0/})).toBeVisible()

        // One release type means the version list is where the wizard opened, so leaving starts here.
        await page.getByRole('button', {name: 'Close'}).click()
        await expect(page.getByRole('button', {name: /1\.0\.0/})).toHaveCount(0)
    })
})

test.describe('All four steps, through to the file', () => {
    test('an anonymous visitor walks a free product to a real download', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const releaseTypes = await (await request.get(`/api/v1/products/${id}/release-types`)).json() as
            {id: string}[]
        expect(releaseTypes.map(entry => entry.id)).toContain('STABLE')

        const versions = await (await request.get(
            `/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {version: string; downloadTypeIds: number[]}[]
        expect(versions.length).toBeGreaterThan(1)
        // Newest first, which is what lets somebody pick the current build without reading them all.
        expect(versions[0]!.version).toBe('1.1.0')

        const newest = versions[0]!
        const issued = await request.post(
            `/api/v1/products/${id}/versions/${newest.version}/downloads/${newest.downloadTypeIds[0]}/issue`)
        expect(issued.status()).toBe(201)
        const {url, filename, sizeBytes} = await issued.json()
        expect(filename).toBe('E2EFreebie-1.1.0.jar')
        expect(sizeBytes).toBeGreaterThan(0)

        const file = await request.get(url)

        expect(file.ok()).toBe(true)
        expect(file.headers()['content-disposition']).toContain('E2EFreebie-1.1.0.jar')
        // A jar is a zip, and a zip says so in its first two bytes.
        expect((await file.body()).subarray(0, 2).toString('latin1')).toBe('PK')
    })

    test('the link is good once and once only', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')
        const versions = await (await request.get(
            `/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {version: string; downloadTypeIds: number[]}[]
        const newest = versions[0]!
        const {url} = await (await request.post(
            `/api/v1/products/${id}/versions/${newest.version}/downloads/${newest.downloadTypeIds[0]}/issue`)).json()

        expect((await request.get(url)).ok()).toBe(true)

        const second = await request.get(url)
        expect(second.ok()).toBe(false)
    })

    test('an older version can still be chosen', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')
        const versions = await (await request.get(
            `/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {version: string; downloadTypeIds: number[]}[]
        const oldest = versions[versions.length - 1]!

        const issued = await request.post(
            `/api/v1/products/${id}/versions/${oldest.version}/downloads/${oldest.downloadTypeIds[0]}/issue`)

        expect(issued.status()).toBe(201)
        expect((await issued.json()).filename).toBe(`E2EFreebie-${oldest.version}.jar`)
    })

    test("a signed-in visitor's download shows up in their own history", async ({request}) => {
        const signup = await request.post('/api/auth/signup', {
            data: {email: uniqueEmail('wizard-history'), password: PASSWORD},
        })
        const {token} = await signup.json()
        const authorized = {Authorization: `Bearer ${token}`}
        const id = await productId(request, 'E2E Freebie')
        const versions = await (await request.get(
            `/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {version: string; downloadTypeIds: number[]}[]
        const newest = versions[0]!

        const {url} = await (await request.post(
            `/api/v1/products/${id}/versions/${newest.version}/downloads/${newest.downloadTypeIds[0]}/issue`,
            {headers: authorized})).json()
        expect((await request.get(url)).ok()).toBe(true)

        const history = await request.get('/api/account/downloads', {headers: authorized})

        expect(history.ok()).toBe(true)
        const {rows} = await history.json() as {rows: {version: string; source: string}[]}
        expect(rows.map(row => row.version)).toContain('1.1.0')
        expect(rows[0]!.source).toBe('free')
    })

    test('a version nobody published is not found', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const issued = await request.post(`/api/v1/products/${id}/versions/9.9.9/downloads/1/issue`)

        expect(issued.status()).toBe(404)
    })
})
