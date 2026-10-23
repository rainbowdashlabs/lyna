/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/**
 * Licenses found, given release types and ended on the licenses page, and Ko-fi codes unlinked.
 */
test.describe('Managing licenses', () => {
    test('a license is found by search, reaches a release type, and is deleted after a second click', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Licensed')
        const identifier = uniqueName('buyer').replace(' ', '-')
        const id = await createProduct(operator, name)
        await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier},
        })
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/licenses`)

        await page.getByPlaceholder('Search by product, identifier, id or owner').fill(identifier)
        const row = page.getByRole('listitem', {name: identifier})
        await expect(row).toHaveCount(1)
        await row.getByRole('button', {name: 'Manage'}).click()
        await row.locator('label').filter({hasText: 'DEV'}).getByRole('switch').click()
        await expect(row.locator('label').filter({hasText: 'DEV'}).getByRole('switch')).toHaveAttribute('aria-checked', 'true')

        await page.reload()
        await page.getByPlaceholder('Search by product, identifier, id or owner').fill(identifier)
        await row.getByRole('button', {name: 'Manage'}).click()
        await expect(row.locator('label').filter({hasText: 'DEV'}).getByRole('switch')).toHaveAttribute('aria-checked', 'true')

        await row.getByRole('button', {name: 'Delete license'}).click()
        await row.getByRole('button', {name: 'Really delete'}).click()
        await expect(row).toHaveCount(0)
        await deleteProduct(operator, id, name)
    })

    test('a license held by an account without Discord names that account, not "unclaimed"', async ({page, signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Held')
        const identifier = uniqueName('buyer').replace(' ', '-')
        const id = await createProduct(operator, name)
        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {data: {productId: id, identifier}})).json()
        const holder = await signedUp('holder')
        expect((await holder.api.post('/api/account/licenses/redeem', {data: {key: issued.key}})).status()).toBe(201)

        const listed = (await (await operator.get(`/api/admin/g/${GUILD}/licenses`)).json())
            .find((license: {identifier: string}) => license.identifier === identifier)
        expect(listed.owner).toBeNull()
        expect(listed.holder).toBe(holder.email)

        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/licenses`)
        await page.getByPlaceholder('Search by product, identifier, id or owner').fill(identifier)
        const row = page.getByRole('listitem', {name: identifier})
        await expect(row).toContainText(`owner ${holder.email}`)
        await expect(row).not.toContainText('unclaimed')
        await deleteProduct(operator, id, name)
    })

    test('a license issued on the page goes to whoever proved its address, and to nobody who merely claimed it', async ({signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Handed')
        const id = await createProduct(operator, name)
        const unproved = await signedUp('unproved')

        const handed = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: OPERATOR.email},
        })).json()
        const kept = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: unproved.email},
        })).json()

        expect(handed.holder).not.toBeNull()
        expect(kept.holder).toBeNull()
        const held = await (await operator.get('/api/account/licenses')).json()
        expect(JSON.stringify(held)).toContain(name)
        expect(JSON.stringify(await (await unproved.api.get('/api/account/licenses')).json())).not.toContain(name)
        await deleteProduct(operator, id, name)
    })

    test('Discord ids are sent as text, so they arrive exactly', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const discordId = '1234567890123456789'

        const registration = await (await operator.get(`/api/admin/g/${GUILD}/registrations/${discordId}`)).json()

        expect(registration.discordId).toBe(discordId)
    })

    test('looking up somebody who registered nothing says so', async ({page}) => {
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/registrations`)

        await page.getByPlaceholder('Discord user id').fill('1234567890123456789')
        await page.getByRole('button', {name: 'Look up'}).click()

        await expect(page.getByText('No registration found.')).toBeVisible()
    })

    test('a license issued without naming release types reaches stable builds', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Defaulted')
        const id = await createProduct(operator, name)

        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: uniqueName('buyer').replace(' ', '-')},
        })).json()
        const access = await (await operator.get(`/api/admin/g/${GUILD}/licenses/${issued.id}/access`)).json()

        expect(access.releaseTypes).toEqual(['STABLE'])
        await deleteProduct(operator, id, name)
    })

    test('a license issued on the page reaches the release types ticked there', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Chosen')
        const identifier = uniqueName('buyer').replace(' ', '-')
        const id = await createProduct(operator, name)
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/licenses`)

        await page.getByRole('button', {name: 'Issue license'}).click()
        await page.getByLabel('Product').selectOption({label: name})
        await page.getByLabel('Identifier (mail / order id)').fill(identifier)
        await page.locator('label').filter({hasText: 'STABLE'}).getByRole('checkbox').uncheck()
        await page.locator('label').filter({hasText: 'DEV'}).getByRole('checkbox').check()
        await page.getByRole('button', {name: 'Issue', exact: true}).click()
        await expect(page.getByText('License issued')).toBeVisible()

        const licenses = await (await operator.get(`/api/admin/g/${GUILD}/licenses`)).json()
        const issued = licenses.find((license: {identifier: string}) => license.identifier === identifier)
        const access = await (await operator.get(`/api/admin/g/${GUILD}/licenses/${issued.id}/access`)).json()
        expect(access.releaseTypes).toEqual(['DEV'])
        await deleteProduct(operator, id, name)
    })

    test('a Ko-fi code is unlinked from the Ko-fi page', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Sold')
        const code = `e2e${Date.now()}`
        const id = await createProduct(operator, name)
        await operator.post(`/api/admin/g/${GUILD}/kofi`, {data: {linkCode: code, productId: id}})
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/kofi`)

        const mapping = page.getByRole('listitem').filter({hasText: code})
        await mapping.getByRole('button', {name: 'Remove'}).click()

        await expect(mapping).toHaveCount(0)
        await deleteProduct(operator, id, name)
    })
})
