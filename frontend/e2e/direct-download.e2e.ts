/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, uniqueName} from './fixtures/admin'
import {uniqueEmail} from './fixtures/unique'

async function downloadType(operator: APIRequestContext, releaseType: 'STABLE' | 'DEV'): Promise<number> {
    const created = await operator.post(`/api/admin/g/${GUILD}/download-types`, {
        data: {name: uniqueName(releaseType), description: '', releaseType},
    })
    expect(created.ok()).toBe(true)
    return (await created.json()).id
}

async function offer(operator: APIRequestContext, productId: number, typeId: number): Promise<void> {
    const added = await operator.post(`/api/admin/g/${GUILD}/products/${productId}/downloads`, {
        data: {typeId, repository: 'releases', groupId: 'de.chojo', artifactId: 'e2e-plugin', classifier: null},
    })
    expect(added.ok()).toBe(true)
}

/**
 * The lasting address of one build, which anybody can type rather than be handed by the wizard. Only a
 * free product is served there: a paid build goes through a one-time token, which is where the jar is
 * stamped with who it was issued to and the key of their license.
 */
test.describe('A direct download', () => {
    test('refuses a paid product, even to somebody holding its license', async ({signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Paid')
        const id = await createProduct(operator, name)
        const stable = await downloadType(operator, 'STABLE')
        await offer(operator, id, stable)
        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: uniqueEmail('paid-buyer'), releaseTypes: ['STABLE']},
        })).json()
        const {api: buyer} = await signedUp('paid-buyer')
        expect((await buyer.post('/api/account/licenses/redeem', {data: {key: issued.key}})).ok()).toBe(true)

        expect((await buyer.get(`/api/v1/download/direct/${id}/${stable}/latest`)).status()).toBe(403)

        await deleteProduct(operator, id, name)
        await operator.delete(`/api/admin/g/${GUILD}/download-types/${stable}`)
    })

    test('serves a free product to anybody', async ({request, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Free')
        const id = await createProduct(operator, name, true)
        const stable = await downloadType(operator, 'STABLE')
        await offer(operator, id, stable)

        expect((await request.get(`/api/v1/download/direct/${id}/${stable}/latest`)).status()).toBe(200)

        await deleteProduct(operator, id, name)
        await operator.delete(`/api/admin/g/${GUILD}/download-types/${stable}`)
    })
})
