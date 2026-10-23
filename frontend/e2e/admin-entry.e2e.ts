/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {logIn, PASSWORD, signUp} from './fixtures/auth'

/**
 * How somebody finds the admin area, and what somebody who cannot administer anything is told there.
 * The seeded account is an operator of the stack.
 */
test.describe('The way into the admin area', () => {
    test('an operator finds it among the account tabs and on the storefront', async ({page}) => {
        await logIn(page, {email: 'entitled@example.invalid', password: PASSWORD})
        await expect(page).toHaveURL(/\/account$/)

        await expect(page.getByRole('link', {name: 'Admin', exact: true})).toBeVisible()
        await page.goto('/')
        await page.getByRole('link', {name: 'Admin', exact: true}).click()

        await expect(page).toHaveURL(/\/admin\/(g\/\d+\/products|select)$/)

        await page.getByRole('link', {name: 'Your account', exact: true}).click()
        await expect(page).toHaveURL(/\/account$/)
    })

    test('choosing a guild from the instance area opens that guild\'s products', async ({page}) => {
        await logIn(page, {email: 'entitled@example.invalid', password: PASSWORD})
        await expect(page).toHaveURL(/\/account$/)
        await page.goto('/admin/instance/system')

        await page.getByRole('combobox').selectOption('4242')

        await expect(page).toHaveURL(/\/admin\/g\/4242\/products$/)
    })

    /**
     * An instance with no bot, no configured guild and no products has no guild to list. The stack
     * has products, so the guilds are taken out of the answer here; whether the caller is an
     * operator is still the backend's word.
     */
    test('an operator with no guild to administer is taken to the instance area', async ({page}) => {
        await page.route('**/api/admin/access', async route => {
            const response = await route.fetch()
            const access = await response.json()
            await route.fulfill({response, json: {...access, guilds: []}})
        })
        await logIn(page, {email: 'entitled@example.invalid', password: PASSWORD})

        await page.getByRole('link', {name: 'Admin', exact: true}).click()

        await expect(page).toHaveURL(/\/admin\/instance\/system$/)
        await expect(page.getByRole('link', {name: 'Instance area'})).toBeVisible()
    })

    test('an account that administers nothing is offered no way in, and told why at the door', async ({page}) => {
        await signUp(page, 'no-admin')

        await expect(page.getByRole('link', {name: 'Admin', exact: true})).toHaveCount(0)
        await page.goto('/admin')

        await expect(page.getByRole('heading', {name: 'No admin access'})).toBeVisible()
        await expect(page.getByText('Administering a guild needs your Discord account.')).toBeVisible()
    })
})
