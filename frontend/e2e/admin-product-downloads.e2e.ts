/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/**
 * A product's downloads and the roles that reach them, managed on the product's page. The stack runs
 * no bot, so roles are typed by id.
 */
test.describe('A product\'s downloads and access', () => {
    test('a download is added against Nexus, edited, and removed', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Downloadable')
        const typeName = uniqueName('Kind')
        const id = await createProduct(operator, name)
        await operator.post(`/api/admin/g/${GUILD}/download-types`, {
            data: {name: typeName, description: '', releaseType: 'STABLE'},
        })
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=downloads`)

        await page.getByLabel('Download type').selectOption({label: `${typeName} (STABLE)`})
        await page.getByLabel('Repository').fill('releases')
        await page.getByLabel('Group id').fill('de.chojo')
        await page.getByLabel('Artifact id').fill('e2e-plugin')
        await page.getByRole('button', {name: 'Add download'}).click()

        const row = page.getByRole('listitem', {name: typeName, exact: true})
        await expect(row.getByText(/Nexus: /)).toBeVisible()
        await row.getByLabel('Artifact id').fill('nothing-here')
        await row.getByRole('button', {name: 'Save'}).click()
        await expect(row.getByRole('button', {name: 'Save'})).toBeEnabled()
        await page.reload()
        await expect(row.getByLabel('Artifact id')).toHaveValue('nothing-here')

        await row.getByRole('button', {name: 'Remove'}).click()
        await expect(row).toHaveCount(0)
        await deleteProduct(operator, id, name)
    })

    test('a role is granted a release type and has it revoked again', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Guarded')
        const id = await createProduct(operator, name)
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=access`)

        await page.getByPlaceholder('Role id').fill('123456789')
        await page.getByLabel('Release type').selectOption('DEV')
        await page.getByRole('button', {name: 'Grant'}).click()

        const grant = page.getByRole('listitem', {name: '123456789'})
        await expect(grant).toContainText('DEV')
        await grant.getByRole('button', {name: 'Revoke'}).click()
        await expect(grant).toHaveCount(0)
        await deleteProduct(operator, id, name)
    })
})
