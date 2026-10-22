/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, OPERATOR, uniqueName} from './fixtures/admin'

/**
 * A product page's release notes, read from the GitHub repository the product names. The stack's
 * stand-in answers for e2e-owner/e2e-plugin the way GitHub would, draft included.
 */
const REPOSITORY = 'https://github.com/e2e-owner/e2e-plugin'

test.describe('Release notes on a product page', () => {
    test('the tab lists the releases newest first, renders their notes and leaves drafts out', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Released')
        const id = await createProduct(operator, name, true, {url: REPOSITORY})

        await page.goto(`/products/${id}`)
        await page.getByRole('button', {name: 'Release notes'}).click()
        await expect(page).toHaveURL(/[?&]tab=releases/)

        const notes = page.getByTestId('release-notes')
        await expect(notes.getByRole('heading')).toHaveText(['Spooky beta', 'v1.0.0'])
        await expect(notes.getByText('Pre-release')).toHaveCount(1)
        await expect(notes.locator('.prose-markdown strong')).toHaveText('ghosts')
        await expect(notes.locator('.prose-markdown li')).toHaveCount(2)
        await expect(notes.getByText('This release came without notes.')).toBeVisible()
        await expect(notes.getByText('Unfinished draft')).toHaveCount(0)
        await expect(notes.getByRole('link', {name: 'On GitHub'}).first()).toHaveAttribute(
            'href', 'https://github.com/e2e-owner/e2e-plugin/releases/tag/v2.0.0-beta')

        await page.reload()
        await expect(notes.getByRole('heading')).toHaveText(['Spooky beta', 'v1.0.0'])
        await page.getByRole('button', {name: 'Overview'}).click()
        await expect(page.getByTestId('release-notes')).toHaveCount(0)
        await deleteProduct(operator, id, name)
    })

    test('the endpoint answers anybody, and a product naming no GitHub repository has no tab and no notes', async ({page, request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const withRepository = uniqueName('Repo')
        const withRepositoryId = await createProduct(operator, withRepository, true, {url: REPOSITORY})
        const without = uniqueName('NoRepo')
        const withoutId = await createProduct(operator, without, true, {url: 'https://example.invalid/project'})

        const notes = await request.get(`/api/v1/products/${withRepositoryId}/release-notes`)
        expect(notes.status()).toBe(200)
        expect((await notes.json()).map((release: {tag: string}) => release.tag)).toEqual(['v2.0.0-beta', 'v1.0.0'])
        expect(await (await request.get(`/api/v1/products/${withoutId}/release-notes`)).json()).toEqual([])
        expect((await request.get('/api/v1/products/999999/release-notes')).status()).toBe(404)

        await page.goto(`/products/${withoutId}?tab=releases`)
        await expect(page.getByRole('heading', {name: without})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Release notes'})).toHaveCount(0)
        await expect(page.getByTestId('release-notes')).toHaveCount(0)

        await deleteProduct(operator, withRepositoryId, withRepository)
        await deleteProduct(operator, withoutId, without)
    })
})
