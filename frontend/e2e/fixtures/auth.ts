/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, type Page} from '@playwright/test'
import {uniqueEmail} from './unique'

export interface Account {
    email: string
    password: string
}

/**
 * The password every story signs up with. Long enough for the rule the signup form enforces, and the
 * same everywhere because it is the address that tells the accounts apart, not the password.
 */
export const PASSWORD = 'end-to-end-password'

/**
 * Signs a brand new account up through the form, the way a visitor would.
 *
 * <p>Going through the page rather than posting to the endpoint is deliberate: it is the one place
 * the suite has to know that signing up works at all, and every story that needs to *be* someone
 * gets that for free.
 *
 * @return the account that is now signed in on this page
 */
export async function signUp(page: Page, prefix: string): Promise<Account> {
    const account: Account = {email: uniqueEmail(prefix), password: PASSWORD}

    await page.goto('/signup')
    await fillCredentials(page, account, {confirm: true})
    await page.getByRole('button', {name: 'Sign up'}).click()

    await expect(page).toHaveURL(/\/account$/)
    return account
}

/**
 * Signs an existing account in through the form. Does not wait for the outcome, so a story can
 * assert either the account area or the error the form shows.
 */
export async function logIn(page: Page, account: Account): Promise<void> {
    await page.goto('/login')
    await fillCredentials(page, account, {confirm: false})
    await page.getByRole('button', {name: 'Log in'}).click()
}

async function fillCredentials(page: Page, account: Account, options: {confirm: boolean}): Promise<void> {
    await page.locator('input[type="email"]').fill(account.email)
    await page.locator('input[type="password"]').first().fill(account.password)
    if (options.confirm) {
        await page.locator('input[type="password"]').nth(1).fill(account.password)
    }
}

/**
 * Signs out through the control the layout offers, whichever of its two the viewport shows.
 */
export async function logOut(page: Page): Promise<void> {
    await page.getByRole('button', {name: 'Log out'}).filter({visible: true}).first().click()
}

/**
 * Throws away the stored session without telling the backend, for stories about what a visitor
 * without one sees.
 */
export async function forgetSession(page: Page): Promise<void> {
    await page.evaluate(() => window.localStorage.clear())
}

/**
 * @return the session token the application stored, or null when it stored none
 */
export function storedToken(page: Page): Promise<string | null> {
    return page.evaluate(() => window.localStorage.getItem('auth'))
}
