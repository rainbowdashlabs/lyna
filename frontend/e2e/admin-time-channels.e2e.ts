/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from './fixtures/session'
import {GUILD, OPERATOR, operatorPage} from './fixtures/admin'

/**
 * The time channels page. The stack runs no bot, so what it can show is the page saying so, and the
 * endpoint refusing a channel it cannot rename.
 */
test.describe('The time channels page', () => {
    test('without a bot it says that none can be set up, and setting one is refused', async ({page, signedIn}) => {
        const operator = await signedIn(OPERATOR)
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/time-channels`)

        await expect(page.getByRole('heading', {name: 'Time channels'})).toBeVisible()
        await expect(page.getByText('The bot renames these channels, and none is connected')).toBeVisible()
        await expect(page).toHaveTitle('Time channels · Lyna')

        const refused = await operator.post(`/api/admin/g/${GUILD}/time-channels`, {
            data: {channelId: '1', zone: 'Europe/Berlin', template: null},
        })
        expect(refused.status()).toBe(409)
        const zones = await operator.get(`/api/admin/g/${GUILD}/time-channels/zones?q=berlin`)
        expect(await zones.json()).toContain('Europe/Berlin')
    })

    /** No channel can be set up without a bot, so the list is answered here rather than by the stack. */
    test('stopping a channel asks for confirmation first', async ({page}) => {
        const channel = {channelId: '77', channelName: 'berlin-time', zone: 'Europe/Berlin', template: '{time}', shows: '12:00'}
        const deleted: string[] = []
        await page.route(`**/api/admin/g/${GUILD}/time-channels`, route => route.fulfill({json: [channel]}))
        await page.route(`**/api/admin/g/${GUILD}/time-channels/77`, async route => {
            deleted.push(route.request().method())
            await route.fulfill({status: 204})
        })
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/time-channels`)

        const row = page.getByRole('listitem', {name: 'berlin-time'})
        await row.getByRole('button', {name: 'Stop'}).click()
        expect(deleted).toEqual([])
        await row.getByRole('button', {name: 'Confirm'}).click()

        await expect.poll(() => deleted).toEqual(['DELETE'])
    })
})
