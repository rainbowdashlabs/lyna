/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'

/**
 * The public routes are server-rendered by route rule. With JavaScript switched off nothing can be
 * repaired by hydration, so what the page shows here is what the server actually sent.
 */
test.describe('Server-rendered public pages', () => {
    test('the storefront arrives with its content already in the markup', async ({page}) => {
        await page.goto('/')

        await expect(page.getByRole('heading', {level: 1})).toBeVisible()
    })

    test('the login form is in the markup, not built by the browser', async ({page}) => {
        await page.goto('/login')

        await expect(page.locator('input[type="email"]')).toBeVisible()
        await expect(page.locator('input[type="password"]')).toBeVisible()
        await expect(page.getByRole('button', {name: 'Log in'})).toBeVisible()
    })

    test('the signup form is in the markup too', async ({page}) => {
        await page.goto('/signup')

        await expect(page.locator('input[type="email"]')).toBeVisible()
        await expect(page.locator('input[type="password"]')).toHaveCount(2)
    })

    test('the forgotten-password form is in the markup', async ({page}) => {
        await page.goto('/forgot-password')

        await expect(page.locator('input[type="email"]')).toBeVisible()
    })
})
