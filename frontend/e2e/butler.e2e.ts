/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {expect, test} from '@playwright/test'

/**
 * What plugins built against UpdateButler send, asserted through the frontend server they now reach.
 * Their requests and the answers they read are fixed in released jars, so these are the contract.
 */
function report(name: string, lines: number) {
    const log = Array.from({length: lines}, (_, index) =>
        `[12:00:00] [Server thread/${index % 50 === 7 ? 'ERROR' : 'INFO'}]: line ${index} of a long server log, padded to be realistic`).join('\n')
    return {
        pluginMeta: {name, version: '1.2.3', enabled: true, main: 'x.Main', authors: ['a'], loadBefore: [], dependencies: [], softDependencies: [], provides: []},
        serverMeta: {version: 'Paper 1.21', currentPlayers: 3, loadedWorlds: ['world'], plugins: []},
        additionalPluginMeta: [],
        latestLog: {log, pluginLog: 'needle in the plugin log', internalExceptions: ['java.lang.IllegalStateException: boom\n\tat x.Main.run(Main.java:1)'], externalExceptions: []},
        configDumps: [{name: 'plugins/X/config.yml', content: 'enabled: true'}],
        v: 1,
    }
}

test.describe('UpdateButler clients', () => {
    test('an update check for an id nobody mapped is answered the way Butler did', async ({request}) => {
        const response = await request.get('/check?id=987654&version=1.0.0&devbuild=false')

        expect(response.status()).toBe(400)
        expect(await response.json()).toEqual({newVersionAvailable: false, latestVersion: null, hash: null})
    })

    test('the newer client, whose query lost its equals sign, is answered in Butler\'s shape too', async ({request}) => {
        const response = await request.get('/api/v1/update/check?version1.0.0&id=987654')

        expect(response.status()).toBe(400)
        expect(await response.json()).toEqual({newVersionAvailable: false, latestVersion: null, hash: null})
    })

    test('a download for an id nobody mapped is refused', async ({request}) => {
        const response = await request.get('/download?id=987654&version=1.0.0')

        expect(response.status()).toBe(400)
    })

    test('a debug report larger than a megabyte is taken at the old address and answered with both keys', async ({request}) => {
        const response = await request.post('/debug/v1/submit', {data: report('Uploaded', 20_000)})

        expect(response.status()).toBe(200)
        const keys = await response.json()
        expect(keys.hash).toMatch(/^[0-9a-f]{64}$/)
        expect(keys.deletionHash).toMatch(/^[0-9a-f]{64}$/)
    })

    test('what is not a report is refused', async ({request}) => {
        const response = await request.post('/debug/v1/submit', {data: {nothing: true}})

        expect(response.status()).toBe(422)
    })
})

test.describe('The debug report viewer', () => {
    test('the link printed into the console opens the report, and the search finds across sections', async ({page, request}) => {
        const keys = await (await request.post('/debug/v1/submit', {data: report('Viewed', 500)})).json()

        await page.goto(`/debug/v1/read/${keys.hash}`)
        await expect(page.getByRole('heading', {name: 'Viewed'})).toBeVisible()

        await page.getByPlaceholder('Search the whole report').fill('needle')
        await expect(page.getByRole('tab', {name: /Logs/})).toContainText('1')

        await page.getByRole('tab', {name: /Exceptions/}).click()
        await page.getByPlaceholder('Search the whole report').fill('')
        await expect(page.getByText('java.lang.IllegalStateException: boom')).toBeVisible()
    })

    test('a link to a line opens the log at that line', async ({page, request}) => {
        const keys = await (await request.post('/debug/v1/submit', {data: report('Linked', 500)})).json()

        await page.goto(`/debug/v1/read/${keys.hash}#s0-L120`)

        await expect(page.locator('#s0-L120')).toBeInViewport()
        await expect(page.locator('#s0-L120')).toContainText('line 119 of a long server log')
    })

    test('the delete link asks first, then deletes, and the report is gone', async ({page, request}) => {
        const keys = await (await request.post('/debug/v1/submit', {data: report('Deleted', 10)})).json()

        await page.goto(`/debug/v1/delete/${keys.deletionHash}`)
        expect((await request.get(`/api/v1/debug/${keys.hash}`)).status()).toBe(200)
        await page.getByRole('button', {name: 'Delete report'}).click()
        await expect(page.getByText('The report is deleted.')).toBeVisible()

        expect((await request.get(`/api/v1/debug/${keys.hash}`)).status()).toBe(404)
        await page.goto(`/debug/v1/read/${keys.hash}`)
        await expect(page.getByText('There is no report here.')).toBeVisible()
    })
})
