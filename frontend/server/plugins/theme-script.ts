/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {
    type CustomThemeColors,
    defaultTheme,
    modeVariables,
    offeredThemes,
    pageVariables,
    parseCustomColors,
    type ThemePolicy,
    themeColors,
} from '../../src/theme/policy'

/**
 * Puts the operator's theme into the first response, before the browser paints anything.
 *
 * <p>Without this the page arrives in the fallback palette and swaps to the real one once the app
 * has hydrated and asked for it, which the reader sees as a flash. The style block carries the
 * operator's default with their custom colours over it, and every other theme the operator offers
 * under a `data-theme` attribute. The inline script settles light or dark from what this browser last
 * chose, and sets that attribute to the theme this browser chose where the operator still offers it,
 * so the first paint is already the one the app will settle on.
 */
function themeScript(offered: string[]): string {
    return `<script>try{var d=document.documentElement,m=localStorage.getItem('dark_mode');`
        + `if(m==='light'){d.classList.add('light')}else if(m==='dark'){d.classList.add('dark')}`
        + `else{d.classList.add(window.matchMedia('(prefers-color-scheme:dark)').matches?'dark':'light')}`
        + `var t=localStorage.getItem('theme_name');if(t&&${JSON.stringify(offered)}.indexOf(t)>=0){d.dataset.theme=t}`
        + `}catch(e){}</script>`
}

const CACHE_TTL_MS = 60_000

interface ResolvedTheme {
    policy: ThemePolicy
    customColors: CustomThemeColors | null
}

let cached: {data: ResolvedTheme; expires: number} | null = null

/**
 * The operator's policy, kept for a minute so a page view does not cost a backend round trip.
 * A backend that does not answer leaves the last known value in place, and nothing when there is none.
 */
async function resolveTheme(backendUrl: string, now: number): Promise<ResolvedTheme | null> {
    if (cached && cached.expires > now) return cached.data
    try {
        const response = await fetch(`${backendUrl}/api/theme/public`, {signal: AbortSignal.timeout(2000)})
        if (!response.ok) return cached?.data ?? null
        const policy = await response.json() as ThemePolicy
        const resolved: ResolvedTheme = {policy, customColors: parseCustomColors(policy.customThemeColors)}
        cached = {data: resolved, expires: now + CACHE_TTL_MS}
        return resolved
    } catch {
        return cached?.data ?? null
    }
}

function declarations(variables: Record<string, string>): string {
    return Object.entries(variables).map(([name, value]) => `${name}:${value}`).join(';')
}

function themeRules(theme: string, customColors: CustomThemeColors | null, scope: string): string {
    const colors = themeColors(theme, customColors)
    return `${scope || ':root'}{${declarations(pageVariables(colors))}}`
        + `${scope}.light{${declarations(modeVariables(colors.light, colors.bgLight))}}`
        + `${scope}.dark{${declarations(modeVariables(colors.dark, colors.bgDark))}}`
}

function buildStyle({policy, customColors}: ResolvedTheme): string {
    const fallback = defaultTheme(policy)
    const alternatives = offeredThemes(policy)
        .filter(theme => theme !== fallback)
        .map(theme => themeRules(theme, customColors, `html[data-theme="${theme}"]`))
    return `<style data-ssr-theme>${themeRules(fallback, customColors, '')}${alternatives.join('')}</style>`
}

export default defineNitroPlugin(nitroApp => {
    nitroApp.hooks.hook('render:html', async html => {
        const {backendUrl} = useRuntimeConfig()
        const theme = await resolveTheme(backendUrl, Date.now())
        html.head.unshift(themeScript(theme ? offeredThemes(theme.policy) : []))
        if (theme) html.head.push(buildStyle(theme))
    })
})
