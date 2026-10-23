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
 * The lasting address of one build, which anybody can type rather than be handed by the wizard. It
 * has to ask the same question the wizard does: not whether the caller holds the product, but
 * whether what they hold opens this build's release type.
 */
test.describe('A direct download', () => {
    test('opens only the release types a license covers', async ({signedIn, signedUp}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Layered')
        const id = await createProduct(operator, name)
        const stable = await downloadType(operator, 'STABLE')
        const dev = await downloadType(operator, 'DEV')
        await offer(operator, id, stable)
        await offer(operator, id, dev)
        const issued = await (await operator.post(`/api/admin/g/${GUILD}/licenses`, {
            data: {productId: id, identifier: uniqueEmail('stable-only'), releaseTypes: ['STABLE']},
        })).json()
        const {api: buyer} = await signedUp('stable-buyer')
        expect((await buyer.post('/api/account/licenses/redeem', {data: {key: issued.key}})).ok()).toBe(true)

        expect((await buyer.get(`/api/v1/download/direct/${id}/${stable}/latest`)).status()).toBe(200)
        expect((await buyer.get(`/api/v1/download/direct/${id}/${dev}/latest`)).status()).toBe(403)

        await deleteProduct(operator, id, name)
        await operator.delete(`/api/admin/g/${GUILD}/download-types/${stable}`)
        await operator.delete(`/api/admin/g/${GUILD}/download-types/${dev}`)
    })
})
