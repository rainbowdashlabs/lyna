/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {forgetSession, logIn, logOut, PASSWORD, signUp, storedToken} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

test.describe('Signing up and in', () => {
    test('a new account is created and lands in the account area', async ({page}) => {
        await signUp(page, 'signup')

        await expect(page.getByRole('heading', {level: 1, name: 'Account'})).toBeVisible()
        expect(await storedToken(page)).not.toBeNull()
    })

    test('an address that is already taken is refused', async ({page}) => {
        const account = await signUp(page, 'duplicate')
        await forgetSession(page)

        await page.goto('/signup')
        await page.locator('input[type="email"]').fill(account.email)
        await page.locator('input[type="password"]').first().fill(PASSWORD)
        await page.locator('input[type="password"]').nth(1).fill(PASSWORD)
        await page.getByRole('button', {name: 'Sign up'}).click()

        await expect(page.getByText('An account with this email already exists.')).toBeVisible()
        await expect(page).toHaveURL(/\/signup$/)
    })

    test('two passwords that do not match are refused before anything is sent', async ({page}) => {
        await page.goto('/signup')
        await page.locator('input[type="email"]').fill(uniqueEmail('mismatch'))
        await page.locator('input[type="password"]').first().fill(PASSWORD)
        await page.locator('input[type="password"]').nth(1).fill('something else entirely')

        await page.getByRole('button', {name: 'Sign up'}).click()

        await expect(page.getByText('Passwords do not match.')).toBeVisible()
        expect(await storedToken(page)).toBeNull()
    })

    test('an account signs back in after its session was thrown away', async ({page}) => {
        const account = await signUp(page, 'returning')
        await forgetSession(page)

        await logIn(page, account)

        await expect(page).toHaveURL(/\/account$/)
        expect(await storedToken(page)).not.toBeNull()
    })

    test('the wrong password does not sign anyone in', async ({page}) => {
        const account = await signUp(page, 'wrong-password')
        await forgetSession(page)

        await logIn(page, {...account, password: 'not the password'})

        await expect(page).toHaveURL(/\/login$/)
        expect(await storedToken(page)).toBeNull()
    })

    test('an address nobody registered does not sign anyone in', async ({page}) => {
        await logIn(page, {email: uniqueEmail('stranger'), password: PASSWORD})

        await expect(page).toHaveURL(/\/login$/)
        expect(await storedToken(page)).toBeNull()
    })
})

test.describe('Signing out', () => {
    test('logging out returns to the login page and forgets the session', async ({page}) => {
        await signUp(page, 'logout')

        await logOut(page)

        await expect(page).toHaveURL(/\/login/)
        expect(await storedToken(page)).toBeNull()
    })
})

test.describe('Guarded routes', () => {
    test('the account area sends a visitor without a session to the login page', async ({page}) => {
        await page.goto('/')
        await forgetSession(page)

        await page.goto('/account')

        await expect(page).toHaveURL(/\/login/)
    })

    test('the account area is reachable again once signed in', async ({page}) => {
        await signUp(page, 'guarded')

        await page.goto('/account/security')

        await expect(page.getByRole('heading', {name: 'Security'})).toBeVisible()
    })
})

test.describe('Forgotten passwords', () => {
    test('an unknown address is answered the same way a known one is', async ({page}) => {
        await page.goto('/forgot-password')
        await page.locator('input[type="email"]').fill(uniqueEmail('never-registered'))

        await page.getByRole('button', {name: 'Send reset link'}).click()

        await expect(page.getByText(/If the email matches an account/i)).toBeVisible()
    })
})
