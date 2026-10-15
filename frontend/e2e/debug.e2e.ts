/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {gzipSync} from 'node:zlib'
import {expect, test} from '@playwright/test'

/**
 * Debug reports in the second format, as eldo-util sends them: gzipped, answered with the links to
 * print, and opened at the short addresses.
 */
function report(name: string, version: string) {
    return {
        v: 2,
        plugin: {name, version, main: 'x.Main'},
        server: {version: 'Paper 26.1', currentPlayers: 2, loadedWorlds: ['world'], plugins: []},
        sections: [
            {kind: 'LOG', name: 'latest.log', content: '[12:00:00 INFO]: Done'},
            {kind: 'CONFIG', name: 'config.yml', content: 'enabled: true'},
        ],
    }
}

function gzipped(body: unknown) {
    return gzipSync(Buffer.from(JSON.stringify(body)))
}

const headers = {'Content-Type': 'application/json', 'Content-Encoding': 'gzip'}

test.describe('Debug reports in the second format', () => {
    test('a gzipped report is answered with its links, and the short link opens it', async ({page, request}) => {
        const response = await request.post('/api/v1/debug/submit', {headers, data: gzipped(report('Short', '1.0.0'))})

        expect(response.status()).toBe(200)
        const receipt = await response.json()
        expect(receipt.readKey).toMatch(/^[0-9a-f]{64}$/)
        expect(receipt.readUrl).toMatch(new RegExp(`/debug/${receipt.readKey}$`))
        expect(receipt.deleteUrl).toMatch(new RegExp(`/debug/delete/${receipt.deleteKey}$`))
        expect(Date.parse(receipt.expires)).toBeGreaterThan(Date.now())

        await page.goto(`/debug/${receipt.readKey}`)
        await expect(page.getByRole('heading', {name: 'Short'})).toBeVisible()
    })

    test('a report from an older version of a product says a newer one is out', async ({page, request}) => {
        const receipt = await (await request.post('/api/v1/debug/submit', {headers, data: gzipped(report('E2E Freebie', '1.0.0'))})).json()

        await page.goto(`/debug/${receipt.readKey}`)
        await expect(page.getByText('Version 1.1.0 is out; this report comes from 1.0.0.', {exact: false})).toBeVisible()
    })

    test('a report from the latest version says nothing about versions', async ({page, request}) => {
        const receipt = await (await request.post('/api/v1/debug/submit', {headers, data: gzipped(report('E2E Freebie', '1.1.0'))})).json()

        await page.goto(`/debug/${receipt.readKey}`)
        await expect(page.getByRole('heading', {name: 'E2E Freebie'})).toBeVisible()
        await expect(page.getByText('is out; this report comes from', {exact: false})).toHaveCount(0)
    })

    test('the short delete link deletes the report', async ({page, request}) => {
        const receipt = await (await request.post('/api/v1/debug/submit', {data: report('Gone', '1.0.0')})).json()

        await page.goto(`/debug/delete/${receipt.deleteKey}`)
        await page.getByRole('button', {name: 'Delete report'}).click()
        await expect(page.getByText('The report is deleted.')).toBeVisible()
        expect((await request.get(`/api/v1/debug/${receipt.readKey}`)).status()).toBe(404)
    })

    test('a report that breaks a rule is refused with the reason', async ({request}) => {
        const broken = {...report('Broken', '1.0.0'), sections: [{kind: 'BLOB', content: 'x'}]}
        const response = await request.post('/api/v1/debug/submit', {data: broken})

        expect(response.status()).toBe(422)
        const problem = await response.json()
        expect(problem.error).toBe('invalid')
        expect(problem.message).toContain('unknown kind BLOB')
    })

    test('a gzipped body that unpacks past the limit is refused', async ({request}) => {
        const padding = 'x'.repeat(17 * 1024 * 1024)
        const huge = {...report('Huge', '1.0.0'), sections: [{kind: 'LOG', content: padding}]}
        const response = await request.post('/api/v1/debug/submit', {headers, data: gzipped(huge)})

        expect(response.status()).toBe(413)
        expect((await response.json()).error).toBe('too_large')
    })

    test('display hints: checks first, metadata as a table, configs by folder, exceptions counted', async ({page, request}) => {
        const hinted = {
            ...report('Hinted', '1.0.0'),
            sections: [
                {kind: 'CHECK', name: 'Database', content: 'Reachable in 3 ms', level: 'OK'},
                {kind: 'CHECK', name: 'World nether', content: 'Not loaded', level: 'ERROR'},
                {kind: 'META', name: 'Hooks', entries: [{key: 'WorldGuard', value: '7.0.9'}]},
                {kind: 'INTERNAL_EXCEPTION', name: 'Plugin exception 1', content: 'java.lang.IllegalStateException: boom\n\tat x.Main.run(Main.java:1)', count: 4, firstSeen: '12:00:01', lastSeen: '12:30:00'},
                {kind: 'CONFIG', name: 'config.yml', content: 'enabled: true'},
                {kind: 'CONFIG', name: 'mobs/zombie.json', content: '{"speed": 1.5}', format: 'json'},
            ],
        }
        const receipt = await (await request.post('/api/v1/debug/submit', {data: hinted})).json()

        await page.goto(`/debug/${receipt.readKey}`)
        const checks = page.getByRole('listitem')
        await expect(checks.first()).toContainText('World nether')
        await expect(checks.nth(1)).toContainText('Database')
        await expect(page.getByText('WorldGuard', {exact: true})).toBeVisible()
        await expect(page.getByText('7.0.9', {exact: true})).toBeVisible()

        await page.getByRole('tab', {name: /Exceptions/}).click()
        await expect(page.getByText('4×')).toBeVisible()
        await expect(page.getByText('First at 12:00:01, last at 12:30:00')).toBeVisible()

        await page.getByRole('tab', {name: /Config/}).click()
        await expect(page.getByText('mobs/', {exact: true})).toBeVisible()
        await expect(page.getByText('zombie.json', {exact: true})).toBeVisible()
    })
})
