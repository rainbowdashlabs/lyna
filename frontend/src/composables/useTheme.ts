/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {DarkMode, type DarkModeValue, type ThemeColors} from '@/theme/themes'
import {
    type CustomThemeColors,
    effectiveTheme,
    FALLBACK_THEME,
    modeVariables,
    pageVariables,
    parseCustomColors,
    type ThemePolicy,
    themeColors,
} from '@/theme/policy'

const THEME_KEY = 'theme_name'
const DARK_MODE_KEY = 'dark_mode'

const activeTheme = ref<string>(FALLBACK_THEME)
const darkMode = ref<DarkModeValue>('system')
const policy = ref<ThemePolicy | null>(null)
const chosen = ref<string | null>(null)
let customColors: CustomThemeColors | null = null
let settled = false

function isDarkActive(): boolean {
    if (typeof document === 'undefined') return false
    return document.documentElement.classList.contains('dark')
}

function setProperties(variables: Record<string, string>) {
    const root = document.documentElement.style
    for (const [name, value] of Object.entries(variables)) root.setProperty(name, value)
}

function currentColors(): ThemeColors {
    return themeColors(activeTheme.value, customColors)
}

function applyTheme(themeKey: string) {
    if (typeof document === 'undefined') return
    activeTheme.value = themeKey
    document.documentElement.dataset.theme = themeKey
    setProperties(pageVariables(currentColors()))
    applyModeColors()
}

function applyModeColors() {
    if (typeof document === 'undefined' || !settled) return
    const colors = currentColors()
    const dark = isDarkActive()
    setProperties(modeVariables(dark ? colors.dark : colors.light, dark ? colors.bgDark : colors.bgLight))
}

/**
 * Shows what this person should see now: their choice where the operator offers it, the operator's
 * default otherwise, with the operator's custom colours over either.
 */
function settle() {
    settled = true
    applyTheme(effectiveTheme(policy.value, chosen.value))
}

function applyDarkMode(mode: DarkModeValue) {
    if (typeof document === 'undefined') return
    const html = document.documentElement
    html.classList.remove('dark', 'light')
    if (mode === DarkMode.DARK) {
        html.classList.add('dark')
    } else if (mode === DarkMode.LIGHT) {
        html.classList.add('light')
    } else {
        const prefersDark = window.matchMedia('(prefers-color-scheme: dark)').matches
        html.classList.add(prefersDark ? 'dark' : 'light')
    }
    applyModeColors()
}

/**
 * Reads what this browser chose last time. The theme itself waits for {@link applyPolicy}: until the
 * operator's policy is known the page keeps the colours the server rendered it with, which already
 * follow that policy, rather than flashing a choice the operator may not allow.
 */
function initFromLocalStorage() {
    if (typeof localStorage === 'undefined') return
    chosen.value = localStorage.getItem(THEME_KEY)
    const savedDarkMode = localStorage.getItem(DARK_MODE_KEY) as DarkModeValue | null
    if (savedDarkMode) darkMode.value = savedDarkMode
    applyDarkMode(darkMode.value)
}

/**
 * Takes the operator's policy and shows the theme it leads to.
 *
 * @param next the policy, or nothing when the backend could not say; the stored choice and the
 *             fallback theme are then all there is to go on
 */
function applyPolicy(next: ThemePolicy | null) {
    policy.value = next
    customColors = parseCustomColors(next?.customThemeColors)
    settle()
}

/**
 * Takes the theme a signed-in account stored, so it follows the account from browser to browser. An
 * account that never chose one is shown the operator's default, whatever this browser chose before.
 */
function adoptAccountChoice(theme: string | null) {
    chosen.value = theme
    if (typeof localStorage !== 'undefined') {
        if (theme) localStorage.setItem(THEME_KEY, theme)
        else localStorage.removeItem(THEME_KEY)
    }
    if (settled) settle()
}

function setTheme(themeKey: string) {
    adoptAccountChoice(themeKey)
}

function setDarkMode(mode: DarkModeValue) {
    darkMode.value = mode
    applyDarkMode(mode)
    if (typeof localStorage !== 'undefined') localStorage.setItem(DARK_MODE_KEY, mode)
}

export function useTheme() {
    return {
        activeTheme: readonly(activeTheme),
        darkMode: readonly(darkMode),
        policy: readonly(policy),
        applyTheme,
        applyDarkMode,
        applyPolicy,
        adoptAccountChoice,
        initFromLocalStorage,
        setTheme,
        setDarkMode,
    }
}
