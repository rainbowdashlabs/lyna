/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'

/**
 * The four questions the download wizard asks, from the outside.
 *
 * <p>The stack serves a catalogue of its own and a Nexus of its own, so these walk the whole way:
 * which release types, which versions, and then the file itself.
 */
const ENTITLED = {email: 'entitled@example.invalid', password: 'end-to-end-password'}

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
    /**
     * @param request whoever is asking: the story's own context for a visitor, an account's for somebody signed in
     */
    async function releaseTypes(request: APIRequestContext, name: string) {
        const id = await productId(request, name)
        const response = await request.get(`/api/v1/products/${id}/release-types`)
        expect(response.ok()).toBe(true)
        return await response.json() as {id: string; downloadable: boolean}[]
    }

    test('a premium product shows a visitor what exists, and that they cannot download it', async ({request}) => {
        expect(await releaseTypes(request, 'E2E Premium')).toEqual([expect.objectContaining({id: 'STABLE', downloadable: false})])
    })

    test('signing in without a license changes nothing about that', async ({signedUp}) => {
        const {api} = await signedUp('wizard-nolicense')

        expect(await releaseTypes(api, 'E2E Premium')).toEqual([expect.objectContaining({downloadable: false})])
    })

    test('a license makes the release type it covers downloadable', async ({signedIn}) => {
        const entitled = await signedIn(ENTITLED)

        expect(await releaseTypes(entitled, 'E2E Premium')).toEqual([expect.objectContaining({id: 'STABLE', downloadable: true})])
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

    test('issuing is refused to somebody signed in without a license', async ({signedUp}) => {
        const {api} = await signedUp('wizard-issue')
        const id = await productId(api, 'E2E Premium')

        const response = await api.post(`/api/v1/products/${id}/versions/1.0.0/downloads/1/issue`)

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
        await expect(page.getByText(/one-time link/i)).toHaveCount(0)
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

    test("a free product's link is its public address, and stays good", async ({request}) => {
        const id = await productId(request, 'E2E Freebie')
        const versions = await (await request.get(
            `/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {version: string; downloadTypeIds: number[]}[]
        const newest = versions[0]!
        const {url, expiresAt} = await (await request.post(
            `/api/v1/products/${id}/versions/${newest.version}/downloads/${newest.downloadTypeIds[0]}/issue`)).json()

        expect(url).toContain(`/api/v1/download/direct/${id}/${newest.downloadTypeIds[0]}/${newest.version}`)
        expect(expiresAt).toBeNull()
        expect((await request.get(url)).ok()).toBe(true)
        expect((await request.get(url)).ok()).toBe(true)
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

    test("a signed-in visitor's download shows up in their own history", async ({request, signedUp}) => {
        const {api} = await signedUp('wizard-history')
        const id = await productId(request, 'E2E Freebie')
        const versions = await (await request.get(
            `/api/v1/products/${id}/release-types/STABLE/versions`)).json() as
            {version: string; downloadTypeIds: number[]}[]
        const newest = versions[0]!

        const {url} = await (await api.post(
            `/api/v1/products/${id}/versions/${newest.version}/downloads/${newest.downloadTypeIds[0]}/issue`)).json()
        expect((await api.get(url)).ok()).toBe(true)

        const history = await api.get('/api/account/downloads')

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
