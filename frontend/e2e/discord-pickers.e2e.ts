/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {Page} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {createProduct, deleteProduct, GUILD, OPERATOR, operatorPage, uniqueName} from './fixtures/admin'

/**
 * A guild as a bot would describe it: a channel outside any category, a category holding a text
 * channel with threads and a forum with a post, a channel the bot may not write in kept for its
 * thread, and a category with nothing in it.
 */
const SIDEBAR = [
    {id: '10', name: 'welcome', kind: 'TEXT', parent: null, selectable: true},
    {id: '1', name: 'Releases', kind: 'CATEGORY', parent: null, selectable: false},
    {id: '2', name: 'announcements', kind: 'NEWS', parent: '1', selectable: true},
    {id: '20', name: 'v1 notes', kind: 'THREAD', parent: '2', selectable: true},
    {id: '21', name: 'v2 notes', kind: 'THREAD', parent: '2', selectable: true},
    {id: '3', name: 'support', kind: 'FORUM', parent: '1', selectable: true},
    {id: '30', name: 'crash on start', kind: 'THREAD', parent: '3', selectable: true},
    {id: '4', name: 'readonly', kind: 'TEXT', parent: '1', selectable: false},
    {id: '40', name: 'open questions', kind: 'THREAD', parent: '4', selectable: true},
    {id: '5', name: 'Empty', kind: 'CATEGORY', parent: null, selectable: false},
]

async function withBot(page: Page): Promise<void> {
    await page.route(`**/api/admin/g/${GUILD}/channels**`, route =>
        route.fulfill({json: {connected: true, channels: SIDEBAR}}))
}

/**
 * Roles and channels picked by name. The stack runs without a bot, so what it can show is the
 * fallback: the answer says nobody is connected, and the page asks for the id instead.
 */
test.describe('Picking roles and channels', () => {
    test('without a bot the directory says so', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)

        expect(await (await operator.get(`/api/admin/g/${GUILD}/roles`)).json()).toEqual({connected: false, roles: []})
        expect(await (await operator.get(`/api/admin/g/${GUILD}/channels`)).json()).toEqual({connected: false, channels: []})
        expect(await (await operator.get(`/api/admin/g/${GUILD}/channels?for=renaming`)).json()).toEqual({connected: false, channels: []})
    })

    test('is shut to a visitor who is not signed in', async ({anonymous}) => {
        const visitor = await anonymous()

        expect((await visitor.get(`/api/admin/g/${GUILD}/roles`)).status()).toBe(401)
        expect((await visitor.get(`/api/admin/g/${GUILD}/channels`)).status()).toBe(401)
    })

    test('the release channel falls back to an id, and says why', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Announced')
        const id = await createProduct(operator, name)
        await operator.post(`/api/admin/g/${GUILD}/products/${id}/release-webhook`)
        await operatorPage(page)

        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=announcements`)

        await expect(page.getByPlaceholder('Channel id').first()).toBeVisible()
        await expect(page.getByText('No bot is connected, so there are no names to choose from. Enter the id.').first()).toBeVisible()
        await deleteProduct(operator, id, name)
    })

    test('channels are laid out like the sidebar, and threads open on request', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        const name = uniqueName('Sidebar')
        const id = await createProduct(operator, name)
        await operator.post(`/api/admin/g/${GUILD}/products/${id}/release-webhook`)
        await operatorPage(page)
        await withBot(page)

        await page.goto(`/admin/g/${GUILD}/products/${id}?tab=announcements`)
        const picker = page.getByRole('button', {name: 'Release channel'})
        await picker.click()
        const list = page.getByRole('listbox')

        await expect(list.getByRole('option', {name: /Releases/})).toBeVisible()
        await expect(list.getByRole('option', {name: /announcements/})).toBeVisible()
        await expect(list.getByText('Empty')).toHaveCount(0)
        await expect(list.getByText('v1 notes')).toHaveCount(0)

        await list.getByRole('option', {name: /announcements/}).getByRole('button', {name: 'Expand'}).click()
        await expect(list.getByText('v2 notes')).toBeVisible()
        await list.getByText('v1 notes').click()
        await expect(list).toHaveCount(0)
        await expect(picker).toContainText('v1 notes')
        await expect(picker).toContainText('Releases › announcements')

        await picker.click()
        await expect(list.getByText('v1 notes')).toBeVisible()
        await list.getByRole('option', {name: /Releases/}).getByText('Releases').click()
        await expect(list.getByRole('option', {name: /announcements/})).toHaveCount(0)
        await page.keyboard.press('Escape')

        await picker.click()
        await page.getByPlaceholder('Search channels').fill('crash')
        await expect(list.getByRole('option', {name: /announcements/})).toHaveCount(0)
        await page.keyboard.press('Enter')
        await expect(picker).toContainText('crash on start')
        await expect(picker).toContainText('Releases › support')

        await picker.click()
        await page.getByPlaceholder('Search channels').fill('')
        await list.getByRole('option', {name: /readonly/}).getByText('readonly').click()
        await expect(list.getByText('open questions')).toBeVisible()

        await deleteProduct(operator, id, name)
    })
})
