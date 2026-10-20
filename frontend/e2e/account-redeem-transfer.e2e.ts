/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {PASSWORD, signUp} from './fixtures/auth'
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
        const {api: receiverApi} = await signedUp('receiver')
        const {username: receiver} = await (await receiverApi.put('/api/account/username', {data: {username: 'receiver'}})).json()

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

/**
 * Somebody who signs up with another person's address has typed it, not proved it. A licence handed to
 * that address must wait for whoever proves it, not land with whoever typed it first.
 */
test.describe('Handing a licence to an address', () => {
    test('a share to an address only typed at sign-up waits as an invite', async ({signedIn, signedUp, anonymous}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Squatted')
        const id = await createProduct(operator, name)
        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: uniqueEmail('bought')},
        })).json()
        const {api: owner} = await signedUp('owner')
        expect((await owner.post('/api/account/licenses/redeem', {data: {key: issued.key}})).status()).toBe(201)
        const victim = uniqueEmail('victim')
        const squatter = await anonymous()
        expect((await squatter.post('/api/auth/signup', {data: {email: victim, password: PASSWORD}})).ok()).toBe(true)

        const share = await owner.post(`/api/account/licenses/${issued.id}/sharees`, {data: {subject: victim}})

        expect(share.status()).toBe(201)
        expect((await share.json()).pending).toBe(true)
        const theirs = await (await squatter.get('/api/account/licenses')).json()
        expect(theirs.shared).toEqual([])
        await deleteProduct(operator, id, name)
    })

    test('a transfer to an address only typed at sign-up is refused', async ({signedIn, signedUp, anonymous}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Unproved')
        const id = await createProduct(operator, name)
        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: uniqueEmail('bought')},
        })).json()
        const {api: owner} = await signedUp('giver')
        expect((await owner.post('/api/account/licenses/redeem', {data: {key: issued.key}})).status()).toBe(201)
        const victim = uniqueEmail('victim')
        const squatter = await anonymous()
        expect((await squatter.post('/api/auth/signup', {data: {email: victim, password: PASSWORD}})).ok()).toBe(true)

        const transfer = await owner.post(`/api/account/licenses/${issued.id}/transfer`, {data: {subject: victim}})

        expect(transfer.status()).toBe(404)
        const theirs = await (await squatter.get('/api/account/licenses')).json()
        expect(theirs.owned).toEqual([])
        await deleteProduct(operator, id, name)
    })
})
