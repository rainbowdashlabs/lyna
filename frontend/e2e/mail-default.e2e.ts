/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/**
 * The purchase mail a product sends without anybody writing one, and the product's wiki, which the
 * mail links and the product page shows.
 */
test.describe('The default purchase mail', () => {
    async function withWiki(operator: APIRequestContext, name: string, wikiUrl: string | null): Promise<number> {
        const id = await createProduct(operator, name, false, {url: 'https://example.invalid/source'})
        const updated = await operator.put(`/api/admin/g/${GUILD}/products/${id}`, {
            data: {
                name, url: 'https://example.invalid/source', roleId: '99', free: false, trial: false,
                iconUrl: null, butlerId: null, wikiUrl, reviewUrl: null,
            },
        })
        expect(updated.status()).toBe(204)
        return id
    }

    async function mailingOf(operator: APIRequestContext, productId: number): Promise<{id: number, usesDefault: boolean}> {
        const mailings = await (await operator.get(`/api/admin/g/${GUILD}/mailing`)).json()
        return mailings.find((mailing: {productId: number}) => mailing.productId === productId)
    }

    test('an empty mail sends the default, with the product\'s wiki linked', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Defaulted')
        const id = await withWiki(operator, name, 'https://example.invalid/wiki')

        const before = await (await operator.get(`/api/admin/g/${GUILD}/guild-mail`)).json()
        expect(before.productsWithoutMail).toContain(name)

        await operator.post(`/api/admin/g/${GUILD}/products/${id}/mailing`, {data: {name}})
        const mailing = await mailingOf(operator, id)
        expect(mailing.usesDefault).toBe(true)

        const preview = await operator.post(`/api/admin/g/${GUILD}/mailing/${mailing.id}/preview`, {data: {blocks: '[]'}})
        const html = await preview.text()
        expect(html).toContain(`Thank you for purchasing ${name}!`)
        expect(html).toContain('href="https://example.invalid/wiki"')
        expect(html).not.toContain('Leave a review')

        await deleteProduct(operator, id, name)
    })

    test('a wiki address that is not on the web is refused', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Bad wiki')
        const id = await createProduct(operator, name)

        const refused = await operator.put(`/api/admin/g/${GUILD}/products/${id}`, {
            data: {
                name, url: null, roleId: '99', free: false, trial: false,
                iconUrl: null, butlerId: null, wikiUrl: 'javascript:alert(1)', reviewUrl: null,
            },
        })

        expect(refused.status()).toBe(400)
        await deleteProduct(operator, id, name)
    })

    test('the guild\'s Discord invite is checked, and fills the mail once set', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Invited')
        const id = await withWiki(operator, name, null)
        await operator.post(`/api/admin/g/${GUILD}/products/${id}/mailing`, {data: {name}})
        const mailing = await mailingOf(operator, id)

        const refused = await operator.put(`/api/admin/g/${GUILD}/guild-mail/contact`, {
            data: {discordUrl: 'discord.gg/nope', links: []},
        })
        expect(refused.status()).toBe(400)

        const saved = await operator.put(`/api/admin/g/${GUILD}/guild-mail/contact`, {
            data: {discordUrl: 'https://example.invalid/discord', links: [{label: 'GitHub', url: 'https://example.invalid/gh'}]},
        })
        expect(saved.status()).toBe(204)
        const html = await (await operator.post(`/api/admin/g/${GUILD}/mailing/${mailing.id}/preview`, {data: {blocks: '[]'}})).text()
        expect(html).toContain('Join the Discord')
        expect(html).toContain('>GitHub</a>')

        await operator.put(`/api/admin/g/${GUILD}/guild-mail/contact`, {data: {discordUrl: null, links: []}})
        await deleteProduct(operator, id, name)
    })

    test('the guild default loads into a product\'s editor as a starting point', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Starter')
        const id = await withWiki(operator, name, null)
        await operator.post(`/api/admin/g/${GUILD}/products/${id}/mailing`, {data: {name}})
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/mailing`)

        await page.getByRole('listitem').filter({hasText: name}).getByRole('button', {name: 'Edit'}).click()
        await expect(page.getByText('Nothing in this mail yet. Add a block to start it.')).toBeVisible()
        await page.getByRole('button', {name: 'Load guild default'}).click()

        await expect(page.getByText('Nothing in this mail yet. Add a block to start it.')).toHaveCount(0)
        await expect(page.getByText('Left out unless these are set: reviewUrl')).toBeVisible()
        await deleteProduct(operator, id, name)
    })
})

test.describe('A product\'s wiki', () => {
    test('is linked from the product page', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Documented')
        const id = await (async () => {
            const created = await createProduct(operator, name, true)
            await operator.put(`/api/admin/g/${GUILD}/products/${created}`, {
                data: {
                    name, url: null, roleId: '99', free: true, trial: false,
                    iconUrl: null, butlerId: null, wikiUrl: 'https://example.invalid/wiki', reviewUrl: null,
                },
            })
            return created
        })()

        await page.goto(`/products/${id}`)

        await expect(page.getByRole('link', {name: 'Wiki'})).toHaveAttribute('href', 'https://example.invalid/wiki')
        await deleteProduct(operator, id, name)
    })
})
