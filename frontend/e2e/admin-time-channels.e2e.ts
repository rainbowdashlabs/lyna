/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'
import {GUILD, operatorHeaders, operatorPage} from './fixtures/admin'

/**
 * The time channels page. The stack runs no bot, so what it can show is the page saying so, and the
 * endpoint refusing a channel it cannot rename.
 */
test.describe('The time channels page', () => {
    test('without a bot it says that none can be set up, and setting one is refused', async ({page, request}) => {
        await operatorPage(page)
        await page.goto(`/admin/g/${GUILD}/time-channels`)

        await expect(page.getByRole('heading', {name: 'Time channels'})).toBeVisible()
        await expect(page.getByText('The bot renames these channels, and none is connected')).toBeVisible()
        await expect(page).toHaveTitle('Time channels · Lyna')

        const refused = await request.post(`/api/admin/g/${GUILD}/time-channels`, {
            headers: await operatorHeaders(request),
            data: {channelId: '1', zone: 'Europe/Berlin', template: null},
        })
        expect(refused.status()).toBe(409)
        const zones = await request.get(`/api/admin/g/${GUILD}/time-channels/zones?q=berlin`, {headers: await operatorHeaders(request)})
        expect(await zones.json()).toContain('Europe/Berlin')
    })
})
