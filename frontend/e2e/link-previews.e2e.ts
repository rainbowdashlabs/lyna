/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, test} from '@playwright/test'
import {operatorPage} from './fixtures/admin'

/**
 * Page titles, and what a link shows when it is shared - read from the server-rendered HTML, which is
 * all Discord's crawler sees.
 */
async function crawl(request: APIRequestContext, path: string): Promise<string> {
    const response = await request.get(path, {headers: {'User-Agent': 'Discordbot/2.0'}})
    expect(response.ok()).toBe(true)
    return response.text()
}

function meta(html: string, property: string): string | undefined {
    return new RegExp(`<meta[^>]+(?:property|name)="${property}"[^>]+content="([^"]*)"`).exec(html)?.[1]
}

function embed(html: string): {component: {type: number, components: {type: number}[]}} {
    const json = /<script[^>]+id="discord:component-embed"[^>]*>([\s\S]*?)<\/script>/.exec(html)?.[1]
    expect(json, 'a Discord component embed').toBeTruthy()
    expect(new TextEncoder().encode(json!).length).toBeLessThanOrEqual(3000)
    return JSON.parse(json!)
}

test.describe('Link previews', () => {
    test('a product page names the product and carries OpenGraph and a Discord embed with a download button', async ({request}) => {
        const products = await (await request.get('/api/v1/products')).json() as {id: number, name: string}[]
        const freebie = products.find(product => product.name === 'E2E Freebie')!

        const html = await crawl(request, `/products/${freebie.id}`)

        expect(html).toContain('<title>E2E Freebie · Lyna</title>')
        expect(meta(html, 'og:title')).toBe('E2E Freebie')
        expect(meta(html, 'og:description')).toContain('A freebie for the stories.')
        const card = embed(html)
        expect(card.component.type).toBe(17)
        expect(JSON.stringify(card)).toContain('"label":"Download"')
        const text = (card.component.components[0] as unknown as {content: string}).content
        expect(text).toContain('A **freebie** for the stories.')
        expect(text).not.toContain('- one')
        expect(text).not.toContain('<img')
    })

    test('the storefront and a debug report have previews of their own', async ({request}) => {
        expect(meta(await crawl(request, '/'), 'og:description')).toMatch(/Browse and download \d+ plugins/)

        const keys = await (await request.post('/debug/v1/submit', {
            data: {pluginMeta: {name: 'Previewed', version: '2.0'}, serverMeta: {version: 'Paper 1.21'}, latestLog: {log: 'x'}, v: 1},
        })).json()
        const report = await crawl(request, `/debug/v1/read/${keys.hash}`)

        expect(report).toContain('<title>Debug report: Previewed 2.0 · Lyna</title>')
        expect(meta(report, 'og:description')).toContain('Paper 1.21')
        embed(report)
    })

    test('the login page names itself in the tab', async ({page}) => {
        await page.goto('/login')
        await expect(page).toHaveTitle('Log in · Lyna')
    })

    test('a private page names itself in the tab once signed in', async ({page}) => {
        await operatorPage(page)
        await expect(page).toHaveTitle('Overview · Lyna')
    })
})
