/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {logIn, PASSWORD, signUp, storedToken} from './fixtures/auth'

test.describe('The account overview', () => {
    test('shows the address the account signed up with, and that Discord is not linked', async ({page}) => {
        const account = await signUp(page, 'overview')

        // Scoped to the content: the layout names the signed-in account in its sidebar as well.
        await expect(page.getByRole('main').getByText(account.email)).toBeVisible()
        await expect(page.getByText('Not linked')).toBeVisible()
    })

    test('counts the session the visitor is using', async ({page}) => {
        await signUp(page, 'sessions-card')

        await expect(page.getByText(/active · last sign-in/)).toBeVisible()
    })

    test('says so when nothing has been downloaded yet', async ({page}) => {
        await signUp(page, 'no-downloads')

        await expect(page.getByText(/No downloads yet/i)).toBeVisible()
    })
})

test.describe('The security page', () => {
    test('lists the session in use and marks it as this device', async ({page}) => {
        await signUp(page, 'this-device')

        await page.goto('/account/security')

        await expect(page.getByText('This device')).toBeVisible()
    })

    test('changing the password retires the old one', async ({page}) => {
        const account = await signUp(page, 'change-password')
        const next = 'a-brand-new-password'

        await page.goto('/account/security')
        await page.locator('input[type="password"]').nth(0).fill(account.password)
        await page.locator('input[type="password"]').nth(1).fill(next)
        await page.locator('input[type="password"]').nth(2).fill(next)
        await page.getByRole('button', {name: 'Update password'}).click()
        await expect(page.getByText(/Password updated|Saved/i)).toBeVisible()

        await page.evaluate(() => window.localStorage.clear())
        await logIn(page, {...account, password: next})
        await expect(page).toHaveURL(/\/account$/)
    })

    test('the old password stops working after it is changed', async ({page}) => {
        const account = await signUp(page, 'old-password')
        const next = 'another-brand-new-password'

        await page.goto('/account/security')
        await page.locator('input[type="password"]').nth(0).fill(account.password)
        await page.locator('input[type="password"]').nth(1).fill(next)
        await page.locator('input[type="password"]').nth(2).fill(next)
        await page.getByRole('button', {name: 'Update password'}).click()
        await expect(page.getByText(/Password updated|Saved/i)).toBeVisible()

        await page.evaluate(() => window.localStorage.clear())
        await logIn(page, {...account, password: PASSWORD})

        await expect(page).toHaveURL(/\/login$/)
        expect(await storedToken(page)).toBeNull()
    })
})

test.describe('The admin area', () => {
    test('an account that administers no guild is sent back to its own area', async ({page}) => {
        await signUp(page, 'not-an-admin')

        await page.goto('/admin')

        await expect(page).toHaveURL(/\/account$/)
    })
})
