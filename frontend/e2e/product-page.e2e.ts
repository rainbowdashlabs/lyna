/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'

/**
 * The page a product has of its own.
 *
 * <p>Reached from the storefront, and carrying the description an operator wrote. The description is
 * markdown, which means it is also the one place where what an operator types becomes markup, so
 * what it refuses to render is as much the point as what it renders.
 */
test.describe('A product page', () => {
    test('is reached by the name on its tile', async ({page}) => {
        await page.goto('/')

        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page).toHaveURL(/\/products\/\d+$/)
        await expect(page.getByRole('heading', {level: 1, name: 'E2E Freebie'})).toBeVisible()
    })

    test('renders the description as markdown rather than as its source', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page.getByRole('heading', {name: 'What it does'})).toBeVisible()
        await expect(page.locator('.prose-description strong')).toHaveText('freebie')
        await expect(page.locator('.prose-description li')).toHaveCount(2)
        await expect(page.getByText('# What it does')).toHaveCount(0)
    })

    test('does not let an operator publish script through the description', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()
        await expect(page.locator('.prose-description')).toBeVisible()

        const attribute = await page.locator('.prose-description img').first()
            .getAttribute('onerror')
            .catch(() => null)
        expect(attribute).toBeNull()
        expect(await page.evaluate(() => (window as unknown as {__xss?: number}).__xss)).toBeUndefined()
    })

    test('offers the download a free product offers on its tile', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page.getByRole('button', {name: 'Download'})).toBeVisible()
    })

    test('a premium product offers the purchase instead', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Premium'})
            .getByRole('link', {name: 'E2E Premium', exact: true}).click()

        await expect(page.getByRole('link', {name: /Buy on Ko-fi/})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Download'})).toHaveCount(0)
    })

    test('a product nobody has written about says so', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Premium'})
            .getByRole('link', {name: 'E2E Premium', exact: true}).click()

        await expect(page.getByText('Nothing has been written about this one yet.')).toBeVisible()
    })

    test('a product that does not exist says so rather than failing', async ({page}) => {
        await page.goto('/products/999999')

        await expect(page.getByText('There is no such product.')).toBeVisible()
    })
})

/**
 * The footer, which is where somebody reading a page can find out what they are looking at.
 */
test.describe('The footer', () => {
    test('says which build the instance is running', async ({page}) => {
        await page.goto('/')

        const footer = page.getByRole('contentinfo')
        await expect(footer).toBeVisible()
        await expect(footer.getByText(/^\d+\.\d+/)).toBeVisible()
    })

    test('links to the source and carries the licence', async ({page}) => {
        await page.goto('/')

        const footer = page.getByRole('contentinfo')
        await expect(footer.getByRole('link', {name: 'Source'})).toHaveAttribute(
            'href', 'https://github.com/rainbowdashlabs/lyna')
        await expect(footer.getByText('AGPL-3.0-only')).toBeVisible()
    })

    test('offers the Discord the instance was configured with', async ({page}) => {
        await page.goto('/')

        await expect(page.getByRole('contentinfo').getByRole('link', {name: 'Add the bot'})).toBeVisible()
    })

    test('is on the product page too', async ({page}) => {
        await page.goto('/')
        await page.getByRole('article').filter({hasText: 'E2E Freebie'})
            .getByRole('link', {name: 'E2E Freebie', exact: true}).click()

        await expect(page.getByRole('contentinfo')).toBeVisible()
    })
})
