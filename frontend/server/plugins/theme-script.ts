/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {contrastTextColor, ensureContrast} from '../../src/theme/contrast'
import {Feel, FEEL_RADIUS, type FeelValue, type ModeColors, THEMES, type ThemeColors} from '../../src/theme/themes'

/**
 * Puts the operator's theme into the first response, before the browser paints anything.
 *
 * <p>Without this the page arrives in the fallback palette and swaps to the real one once the app
 * has hydrated and asked for it, which the reader sees as a flash. The inline script settles light
 * or dark from what this browser last chose, and the style block carries the colours themselves, so
 * the first paint is already right.
 */
const THEME_SCRIPT = `<script>try{var m=localStorage.getItem('dark_mode');if(m==='light'){document.documentElement.classList.add('light')}else if(m==='dark'){document.documentElement.classList.add('dark')}else{document.documentElement.classList.add(window.matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light')}}catch(e){}</script>`

const CACHE_TTL_MS = 60_000

interface ResolvedTheme {
    theme: string
    feel: FeelValue
    customColors: ThemeColors | null
}

let cached: {data: ResolvedTheme; expires: number} | null = null

/**
 * The operator's defaults, kept for a minute so a page view does not cost a backend round trip.
 * A backend that does not answer leaves the last known value in place, and the catalogue default when
 * there is none.
 */
async function resolveTheme(backendUrl: string, now: number): Promise<ResolvedTheme | null> {
    if (cached && cached.expires > now) return cached.data
    try {
        const response = await fetch(`${backendUrl}/api/theme/public`, {signal: AbortSignal.timeout(2000)})
        if (!response.ok) return cached?.data ?? null
        const payload = await response.json() as {
            defaultTheme: string
            defaultFeel: string
            customThemeColors: string | null
        }
        let customColors: ThemeColors | null = null
        if (payload.customThemeColors) {
            try {
                customColors = JSON.parse(payload.customThemeColors) as ThemeColors
            } catch {
                customColors = null
            }
        }
        const resolved: ResolvedTheme = {
            theme: payload.defaultTheme,
            feel: (payload.defaultFeel ?? Feel.ROUNDED) as FeelValue,
            customColors,
        }
        cached = {data: resolved, expires: now + CACHE_TTL_MS}
        return resolved
    } catch {
        return cached?.data ?? null
    }
}

function resolveColors(theme: string, customColors: ThemeColors | null): ThemeColors {
    if (theme === 'custom' && customColors) return customColors
    return THEMES[theme]?.colors ?? THEMES.lyna!.colors
}

function buildModeBlock(mode: ModeColors, pageBg: string): string {
    return [
        `--color-primary:${mode.primary}`,
        `--color-primary-accent:${mode.primaryAccent}`,
        `--color-secondary:${mode.secondary}`,
        `--color-secondary-accent:${mode.secondaryAccent}`,
        `--color-info:${mode.info}`,
        `--color-info-accent:${mode.infoAccent}`,
        `--color-success:${mode.success}`,
        `--color-error:${mode.error}`,
        `--color-primary-text:${contrastTextColor(mode.primary)}`,
        `--color-primary-accent-text:${contrastTextColor(mode.primaryAccent)}`,
        `--color-secondary-text:${contrastTextColor(mode.secondary)}`,
        `--color-secondary-accent-text:${contrastTextColor(mode.secondaryAccent)}`,
        `--color-info-text:${contrastTextColor(mode.info)}`,
        `--color-info-accent-text:${contrastTextColor(mode.infoAccent)}`,
        `--color-success-text:${contrastTextColor(mode.success)}`,
        `--color-error-text:${contrastTextColor(mode.error)}`,
        `--color-primary-badge:${ensureContrast(mode.primaryAccent, pageBg)}`,
        `--color-secondary-badge:${ensureContrast(mode.secondaryAccent, pageBg)}`,
        `--color-info-badge:${ensureContrast(mode.infoAccent, pageBg)}`,
        `--color-success-badge:${ensureContrast(mode.success, pageBg)}`,
        `--color-error-badge:${ensureContrast(mode.error, pageBg)}`,
    ].join(';')
}

function buildStyle(theme: ResolvedTheme): string {
    const colors = resolveColors(theme.theme, theme.customColors)
    const radius = FEEL_RADIUS[theme.feel] ?? FEEL_RADIUS[Feel.ROUNDED]
    const rootBlock = [
        `--color-bg-light:${colors.bgLight}`,
        `--color-bg-light-accent:${colors.bgLightAccent}`,
        `--color-bg-dark:${colors.bgDark}`,
        `--color-bg-dark-accent:${colors.bgDarkAccent}`,
        `--radius-theme:${radius}`,
    ].join(';')
    return `<style data-ssr-theme>:root{${rootBlock}}.light{${buildModeBlock(colors.light, colors.bgLight)}}`
        + `.dark{${buildModeBlock(colors.dark, colors.bgDark)}}</style>`
}

export default defineNitroPlugin(nitroApp => {
    nitroApp.hooks.hook('render:html', async html => {
        html.head.unshift(THEME_SCRIPT)
        const {backendUrl} = useRuntimeConfig()
        const theme = await resolveTheme(backendUrl, Date.now())
        if (theme) html.head.push(buildStyle(theme))
    })
})
