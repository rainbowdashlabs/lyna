/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {PASSWORD, signUp} from './fixtures/auth'
import {createProduct, deleteProduct, GUILD, operatorHeaders, uniqueName} from './fixtures/admin'
import {uniqueEmail} from './fixtures/unique'

/**
 * Redeeming a key in the account area and handing the license to somebody else - what /register and
 * /registrations transfer do on Discord.
 */
test.describe('Licenses in the account area', () => {
    test('a key is redeemed once, then transferred to another account, which holds it from then on', async ({page, request}) => {
        const name = uniqueName('Redeemable')
        const id = await createProduct(request, name)
        const issued = await (await request.post(`/api/admin/g/${GUILD}/licenses`, {
            headers: await operatorHeaders(request),
            data: {productId: id, identifier: uniqueEmail('given')},
        })).json()
        const receiver = uniqueEmail('receiver')
        const signup = await request.post('/api/auth/signup', {data: {email: receiver, password: PASSWORD}})
        const receiverToken = (await signup.json()).token

        await signUp(page, 'redeemer')
        await page.goto('/account/licenses')
        await page.getByLabel('License key').fill(issued.key)
        await page.getByRole('button', {name: 'Redeem'}).click()
        await expect(page.getByText(`The license for ${name} is yours now.`)).toBeVisible()
        await expect(page.getByText(name).first()).toBeVisible()

        await page.getByLabel('License key').fill(issued.key)
        await page.getByRole('button', {name: 'Redeem'}).click()
        await expect(page.getByText('You already hold a license for this product')).toBeVisible()

        await page.goto(`/account/licenses/${issued.id}`)
        await page.getByLabel('Username or email address of the receiving account').fill(receiver)
        await page.getByRole('button', {name: 'Transfer'}).click()
        await page.getByRole('button', {name: `Really give it to ${receiver}`}).click()
        await expect(page).toHaveURL(/\/account\/licenses$/)

        const theirs = await (await request.get('/api/account/licenses', {headers: {Authorization: `Bearer ${receiverToken}`}})).json()
        expect(theirs.owned.some((license: {productName: string}) => license.productName === name)).toBe(true)
        await deleteProduct(request, id, name)
    })

    test('a key nobody issued is refused', async ({page}) => {
        await signUp(page, 'bad-key')
        await page.goto('/account/licenses')
        await page.getByLabel('License key').fill('NOT-A-KEY')
        await page.getByRole('button', {name: 'Redeem'}).click()

        await expect(page.getByText('That is not a license key')).toBeVisible()
    })
})
