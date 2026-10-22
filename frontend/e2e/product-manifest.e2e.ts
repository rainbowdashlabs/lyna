/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext, Page} from '@playwright/test'
import {expect, test} from './fixtures/session'

/**
 * What a build says it needs, read from the manifest published beside its jar. The stub publishes
 * one for each seeded version: 1.0.0 runs on Minecraft 1.20 to 1.20.6, 1.1.0 on 1.21 and newer and
 * depends on the seeded premium product.
 */
async function productId(request: APIRequestContext, name: string) {
    const products = await (await request.get('/api/v1/products')).json() as {id: number; name: string}[]
    const found = products.find(product => product.name === name)
    if (!found) throw new Error(`The seed did not create ${name}`)
    return found.id
}

async function openProduct(page: Page, id: number) {
    await page.goto(`/products/${id}`)
    return page.getByRole('complementary', {name: 'Versions'})
}

test.describe('Manifests', () => {
    test('each version carries its manifest, with a dependency pointed at its product and untrusted links dropped', async ({request}) => {
        const freebie = await productId(request, 'E2E Freebie')
        const premium = await productId(request, 'E2E Premium')

        const versions = await (await request.get(`/api/v1/products/${freebie}/release-types/STABLE/versions`)).json()

        expect(versions[0].version).toBe('1.1.0')
        expect(versions[0].manifest.minecraftMin).toBe('1.21')
        expect(versions[0].manifest.platforms).toEqual(['paper', 'folia'])
        expect(versions[0].manifest.dependencies[0]).toMatchObject({name: 'E2E Premium', required: true, productId: premium})
        expect(versions[0].manifest.dependencies[1].links).toEqual([
            {type: 'modrinth', id: 'placeholderapi', url: 'https://modrinth.com/plugin/placeholderapi'},
        ])
        expect(versions[1].manifest).toMatchObject({minecraftMin: '1.20', minecraftMax: '1.20.6'})
    })

    test('the update check offers only what runs on the Minecraft version asked about, and names what does not', async ({request}) => {
        const id = await productId(request, 'E2E Freebie')

        const old = await (await request.get(`/api/v1/update/check?id=${id}&version=1.0.0&minecraft=1.20.4`)).json()
        expect(old.update).toBe(false)
        expect(old.latest).toBe('1.0.0')
        expect(old.newest).toBe('1.1.0')

        const current = await (await request.get(`/api/v1/update/check?id=${id}&version=1.0.0&minecraft=1.21.1`)).json()
        expect(current.update).toBe(true)
        expect(current.latest).toBe('1.1.0')
        expect(current).not.toHaveProperty('newest')

        const unasked = await (await request.get(`/api/v1/update/check?id=${id}&version=1.0.0`)).json()
        expect(unasked.latest).toBe('1.1.0')
        expect(unasked).not.toHaveProperty('newest')
    })

    test('the product page says what the newest build needs and filters versions by Minecraft version', async ({page, request}) => {
        const premium = await productId(request, 'E2E Premium')
        const versions = await openProduct(page, await productId(request, 'E2E Freebie'))

        await expect(versions.getByText('Minecraft 1.21 and newer'), 'on the version and in what it needs').toHaveCount(2)
        const summary = versions.getByTestId('manifest-summary')
        await expect(summary.getByText('What 1.1.0 needs')).toBeVisible()
        await expect(summary.getByRole('link', {name: 'E2E Premium'})).toHaveAttribute('href', `/products/${premium}`)
        await expect(summary.getByRole('link', {name: 'Modrinth'})).toHaveAttribute('href', 'https://modrinth.com/plugin/placeholderapi')

        await versions.getByTestId('minecraft-filter').selectOption('1.20.6')

        await expect(versions.getByText(/^1\.\d\.0$/)).toHaveText(['1.0.0'])
        await expect(summary.getByText('What 1.0.0 needs')).toBeVisible()
    })

    test('the wizard filters its versions by Minecraft version and says what each runs on', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('button', {name: 'Download'}).click()

        await expect(page.getByRole('button', {name: /^1\.0\.0.*Minecraft 1\.20 – 1\.20\.6/})).toBeVisible()
        await page.getByTestId('minecraft-filter').selectOption('1.21')

        await expect(page.getByRole('button', {name: /^1\.1\.0/})).toBeVisible()
        await expect(page.getByRole('button', {name: /^1\.0\.0/})).toHaveCount(0)
    })
})
