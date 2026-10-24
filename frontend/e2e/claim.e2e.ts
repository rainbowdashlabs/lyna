/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, uniqueName} from './fixtures/admin'
import {PASSWORD, signUp} from './fixtures/auth'
import {uniqueEmail} from './fixtures/unique'

/** Issues a license nobody holds yet and answers its key, as a purchase would. */
async function issue(operator: APIRequestContext, productId: number): Promise<string> {
    const issued = await operator.post(`/api/admin/g/${GUILD}/licenses`, {
        data: {productId, identifier: uniqueEmail('claim-buyer')},
    })
    expect(issued.ok()).toBe(true)
    return (await issued.json()).key
}

/**
 * A claim link, `/claim#<key>`, as a plugin or a BuiltByBit page hands it out.
 */
test.describe('A claim link', () => {
    test('claims the key for somebody signed in, after a click', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Claimed')
        const id = await createProduct(operator, name)
        const key = await issue(operator, id)
        await signUp(page, 'claimer')

        await page.goto(`/claim#${key}`)

        await expect(page.getByTestId('claim-key')).toHaveText(key)
        await expect(page).not.toHaveURL(/#/)
        await page.getByRole('button', {name: 'Claim license'}).click()
        await expect(page.getByTestId('claim-done')).toContainText(name)
        await deleteProduct(operator, id, name)
    })

    test('keeps the key while somebody signs up, and claims it afterwards', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Signed up for')
        const id = await createProduct(operator, name)
        const key = await issue(operator, id)

        await page.goto(`/claim#${key}`)
        await page.getByRole('button', {name: 'Sign up to claim'}).click()
        await expect(page).toHaveURL(/\/signup\?next=%2Fclaim$/)
        await page.locator('input[type="email"]').fill(uniqueEmail('claim-signup'))
        await page.locator('input[type="password"]').first().fill(PASSWORD)
        await page.locator('input[type="password"]').nth(1).fill(PASSWORD)
        await page.getByRole('button', {name: 'Sign up'}).click()

        await expect(page).toHaveURL(/\/claim$/)
        await expect(page.getByTestId('claim-key')).toHaveText(key)
        await page.getByRole('button', {name: 'Claim license'}).click()
        await expect(page.getByTestId('claim-done')).toContainText(name)
        await deleteProduct(operator, id, name)
    })
})
