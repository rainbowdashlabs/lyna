/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {signUp} from './fixtures/auth'

/**
 * The self-service pages as a fresh account sees them. A new account is linked to no Discord id and
 * has downloaded nothing, so every one of these is the empty case - which is the case that has to
 * read as an explanation rather than as a blank page.
 */
test.describe('The licenses page', () => {
    test('offers both tabs and explains that there is nothing yet', async ({page}) => {
        await signUp(page, 'licenses')

        await page.goto('/account/licenses')

        await expect(page.getByRole('heading', {level: 1, name: 'Licenses'})).toBeVisible()
        await expect(page.getByRole('button', {name: /Owned/})).toBeVisible()
        await expect(page.getByText(/You don't own any licenses yet/)).toBeVisible()
    })

    test('the shared tab explains its own empty case', async ({page}) => {
        await signUp(page, 'licenses-shared')
        await page.goto('/account/licenses')

        await page.getByRole('button', {name: /Shared with me/}).click()

        await expect(page.getByText(/No one has shared a license with you/)).toBeVisible()
    })

    test('a license nobody holds is not shown to the curious', async ({page}) => {
        await signUp(page, 'licenses-probe')

        await page.goto('/account/licenses/999999')

        await expect(page.getByText(/does not exist, or is not yours/)).toBeVisible()
    })
})

test.describe('The downloads page', () => {
    test('shows its filters and says the range is empty', async ({page}) => {
        await signUp(page, 'downloads')

        await page.goto('/account/downloads')

        await expect(page.getByRole('heading', {level: 1, name: 'Downloads'})).toBeVisible()
        await expect(page.getByText('No downloads in this date range.')).toBeVisible()
    })
})

test.describe('The appearance page', () => {
    test('offers the themes the operator allows', async ({page}) => {
        await signUp(page, 'appearance')

        await page.goto('/account/appearance')

        await expect(page.getByRole('heading', {level: 1, name: 'Appearance'})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Lyna'})).toBeVisible()
    })

    test('a chosen theme is kept across a reload', async ({page}) => {
        await signUp(page, 'appearance-save')
        await page.goto('/account/appearance')

        await page.getByRole('button', {name: 'Midnight'}).click()
        await page.getByRole('button', {name: 'Save'}).click()
        await expect(page.getByText('Saved.')).toBeVisible()

        await page.reload()

        await expect(page.evaluate(() => window.localStorage.getItem('theme_name'))).resolves.toBe('midnight')
    })

    test('dark mode can be set and survives a reload', async ({page}) => {
        await signUp(page, 'appearance-dark')
        await page.goto('/account/appearance')

        await page.getByRole('combobox').first().selectOption('dark')
        await page.getByRole('button', {name: 'Save'}).click()
        await expect(page.getByText('Saved.')).toBeVisible()

        await page.reload()

        await expect(page.locator('html')).toHaveClass(/dark/)
    })
})

test.describe('The public theme', () => {
    test('is served to anyone, so the first paint is already right', async ({request}) => {
        const response = await request.get('/api/theme/public')

        expect(response.ok()).toBe(true)
        const payload = await response.json()
        expect(payload.defaultTheme).toBe('lyna')
        expect(payload.allowUserTheme).toBe(true)
    })

    test('the server puts the theme into the markup before anything renders', async ({request}) => {
        const response = await request.get('/')

        expect((await response.text())).toContain('data-ssr-theme')
    })
})
