/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Page, test} from '@playwright/test'
import {GUILD, operatorPage} from './fixtures/admin'

/**
 * The site's footer is on every page, public, account and admin alike.
 */
async function hasFooter(page: Page, path: string) {
    await page.goto(path)
    await expect(page.getByRole('contentinfo').getByRole('link', {name: 'Source'})).toBeVisible()
}

test.describe('The footer', () => {
    test('is on the public pages', async ({page}) => {
        for (const path of ['/', '/login', '/signup', '/forgot-password']) await hasFooter(page, path)
    })

    test('is in the account and admin areas', async ({page}) => {
        await operatorPage(page)
        for (const path of ['/account', '/account/licenses', `/admin/g/${GUILD}/products`, `/admin/g/${GUILD}/settings`]) {
            await hasFooter(page, path)
        }
    })
})
