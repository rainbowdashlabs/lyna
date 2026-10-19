/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {signUp} from './fixtures/auth'
import {createProduct, deleteProduct, GUILD, OPERATOR, uniqueName} from './fixtures/admin'
import {uniqueEmail} from './fixtures/unique'

/**
 * Redeeming a key in the account area and handing the license to somebody else - what /register and
 * /registrations transfer do on Discord.
 */
test.describe('Licenses in the account area', () => {
    test('a key is redeemed once, then transferred to another account, which holds it from then on', async ({page, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Redeemable')
        const id = await createProduct(operator, name)
        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: uniqueEmail('given')},
        })).json()
        const {email: receiver, api: receiverApi} = await signedUp('receiver')

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

        const theirs = await (await receiverApi.get('/api/account/licenses')).json()
        expect(theirs.owned.some((license: {productName: string}) => license.productName === name)).toBe(true)
        await deleteProduct(operator, id, name)
    })

    test('a key nobody issued is refused', async ({page}) => {
        await signUp(page, 'bad-key')
        await page.goto('/account/licenses')
        await page.getByLabel('License key').fill('NOT-A-KEY')
        await page.getByRole('button', {name: 'Redeem'}).click()

        await expect(page.getByText('That is not a license key')).toBeVisible()
    })
})
