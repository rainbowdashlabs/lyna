/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, type Page} from '@playwright/test'
import {expect, test} from './fixtures/session'
import {OPERATOR, operatorPage} from './fixtures/admin'
import {signUp} from './fixtures/auth'
import {THEMES} from '../src/theme/themes'

/**
 * What the operator's Appearance settings do to everybody else.
 *
 * <p>These change settings every page reads, which is why they run in a project of their own after
 * every other story, one at a time, and put the shipped defaults back after each one.
 *
 * <p>Each story changes the settings before its page loads anything. The policy is served with a
 * minute of caching, so a page that asked for it before the change would carry on with the old one.
 */
const SHIPPED = {defaultTheme: 'transistor', allowUserTheme: true, enabledThemes: [], customThemeColorsJson: null}

interface Appearance {
    defaultTheme: string
    allowUserTheme: boolean
    enabledThemes: string[]
    customThemeColorsJson: string | null
}

test.describe.configure({mode: 'serial'})
test.use({colorScheme: 'light'})

async function setAppearance(operator: APIRequestContext, changes: Partial<Appearance>): Promise<void> {
    const saved = await operator.put('/api/admin/instance/appearance', {data: {...SHIPPED, ...changes}})
    expect(saved.status(), await saved.text()).toBe(204)
}

/** A custom property as the page resolves it on the root element, once the app has settled. */
async function rootVariable(page: Page, name: string): Promise<string> {
    return page.evaluate(variable => getComputedStyle(document.documentElement).getPropertyValue(variable).trim(), name)
}

async function expectBackground(page: Page, theme: string): Promise<void> {
    await expect.poll(() => rootVariable(page, '--color-bg-light'))
        .toBe(THEMES[theme]!.colors.bgLight)
    await expect(page.locator('html')).toHaveAttribute('data-theme', theme)
}

/** Has this browser remember a theme of its own before anything loads, as an earlier visit would. */
async function rememberTheme(page: Page, theme: string): Promise<void> {
    await page.addInitScript(chosen => window.localStorage.setItem('theme_name', chosen), theme)
}

test.afterEach(async ({signedIn}) => {
    await setAppearance(await signedIn(OPERATOR), SHIPPED)
})

test.describe('The default theme', () => {
    test('is what a visitor sees', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {defaultTheme: 'midnight'})

        await page.goto('/')

        await expectBackground(page, 'midnight')
    })

    test('is what an account that never picked one sees', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {defaultTheme: 'forest'})

        await signUp(page, 'theme-default')

        await expectBackground(page, 'forest')
        await expect(page.evaluate(() => window.localStorage.getItem('theme_name'))).resolves.toBeNull()
    })

    test('gives way to a theme this browser chose while the operator lets people choose', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {defaultTheme: 'forest'})
        await rememberTheme(page, 'midnight')

        await page.goto('/')

        await expectBackground(page, 'midnight')
    })
})

test.describe('With choosing switched off', () => {
    test('everybody sees the default, whatever their browser chose', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {defaultTheme: 'forest', allowUserTheme: false})
        await rememberTheme(page, 'midnight')

        await page.goto('/')

        await expectBackground(page, 'forest')
    })

    test('the appearance page says so instead of offering themes', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {defaultTheme: 'forest', allowUserTheme: false})
        await signUp(page, 'theme-locked')

        await page.goto('/account/appearance')

        await expect(page.getByText('Your operator has set one theme for everyone.', {exact: false})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Midnight'})).toHaveCount(0)
        await expectBackground(page, 'forest')
    })
})

test.describe('The enabled themes', () => {
    test('narrow what the appearance page offers', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {defaultTheme: 'lyna', enabledThemes: ['lyna', 'forest']})
        await signUp(page, 'theme-enabled')

        await page.goto('/account/appearance')

        await expect(page.getByRole('button', {name: 'Forest'})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Lyna'})).toBeVisible()
        await expect(page.getByRole('button', {name: 'Midnight'})).toHaveCount(0)
    })

    test('refuse a theme that does not exist, and keep what was there', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)

        const refused = await operator.put('/api/admin/instance/appearance',
            {data: {...SHIPPED, enabledThemes: ['lyna', 'workbench']}})

        expect(refused.status()).toBe(400)
        expect(await refused.text()).toContain('workbench')
        const stored = await (await operator.get('/api/admin/instance/appearance')).json() as Appearance
        expect(stored.enabledThemes).toEqual([])
    })

    test('refuse a default theme that does not exist', async ({signedIn}) => {
        const refused = await (await signedIn(OPERATOR)).put('/api/admin/instance/appearance',
            {data: {...SHIPPED, defaultTheme: 'workbench'}})

        expect(refused.status()).toBe(400)
    })

    test('the operator is told why a save was refused', async ({page}) => {
        await operatorPage(page)
        await page.goto('/admin/instance/appearance')

        await page.getByLabel('Enabled themes (comma-separated)').fill('transistor, workbench')
        await page.getByRole('button', {name: 'Save'}).click()

        await expect(page.getByText(/Unknown theme id: workbench/)).toBeVisible()
    })
})

test.describe('Custom theme colors', () => {
    test('refuse a token no theme has', async ({signedIn}) => {
        const refused = await (await signedIn(OPERATOR)).put('/api/admin/instance/appearance',
            {data: {...SHIPPED, customThemeColorsJson: '{"accent":"#ffffff"}'}})

        expect(refused.status()).toBe(400)
        expect(await refused.text()).toContain('accent')
    })

    test('refuse a value that is not a color', async ({signedIn}) => {
        const operator = await signedIn(OPERATOR)

        for (const colors of ['{"bgLight":"red;}"}', '{"light":{"primary":"#12345"}}', '{not json']) {
            const refused = await operator.put('/api/admin/instance/appearance',
                {data: {...SHIPPED, customThemeColorsJson: colors}})
            expect(refused.status(), colors).toBe(400)
        }
    })

    test('are laid over the theme that is shown', async ({page, signedIn}) => {
        await setAppearance(await signedIn(OPERATOR), {
            defaultTheme: 'midnight',
            customThemeColorsJson: '{"bgLight":"#123456","bgDark":"#123456","light":{"primary":"#abcdef"},"dark":{"primary":"#abcdef"}}',
        })

        await page.goto('/')

        await expect.poll(() => rootVariable(page, '--color-bg-light')).toBe('#123456')
        await expect.poll(() => rootVariable(page, '--color-primary')).toBe('#abcdef')
        expect(await rootVariable(page, '--color-secondary')).toBe(THEMES.midnight!.colors.light.secondary)
    })

    test('are in the markup the server sends, so the first paint already has them', async ({request, signedIn}) => {
        test.setTimeout(120_000)
        await setAppearance(await signedIn(OPERATOR), {customThemeColorsJson: '{"bgLight":"#654321"}'})

        await expect.poll(async () => (await request.get('/')).text(), {timeout: 90_000, intervals: [5_000]})
            .toContain('--color-bg-light:#654321')
    })
})
