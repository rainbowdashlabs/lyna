/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import {
    DarkMode,
    Feel,
    FEEL_RADIUS,
    THEMES,
    type DarkModeValue,
    type FeelValue,
    type ThemeColors,
} from '@/theme/themes'
import {contrastTextColor} from '@/theme/contrast'

const activeTheme = ref<string>('lyna')
const activeFeel = ref<FeelValue>(Feel.ROUNDED)
const darkMode = ref<DarkModeValue>('system')

function isDarkActive(): boolean {
    if (typeof document === 'undefined') return false
    return document.documentElement.classList.contains('dark')
}

function applyTheme(themeKey: string) {
    if (typeof document === 'undefined') return
    const colors = (THEMES[themeKey]?.colors ?? THEMES.lyna?.colors!)
    const root = document.documentElement.style
    root.setProperty('--color-bg-light', colors.bgLight)
    root.setProperty('--color-bg-light-accent', colors.bgLightAccent)
    root.setProperty('--color-bg-dark', colors.bgDark)
    root.setProperty('--color-bg-dark-accent', colors.bgDarkAccent)
    applyModeColors(colors)
}

function applyModeColors(themeColors?: ThemeColors) {
    if (typeof document === 'undefined') return
    const colors = themeColors ?? THEMES[activeTheme.value]?.colors ?? THEMES.lyna?.colors!
    const mode = isDarkActive() ? colors.dark : colors.light
    const root = document.documentElement.style
    root.setProperty('--color-primary', mode.primary)
    root.setProperty('--color-primary-accent', mode.primaryAccent)
    root.setProperty('--color-secondary', mode.secondary)
    root.setProperty('--color-secondary-accent', mode.secondaryAccent)
    root.setProperty('--color-info', mode.info)
    root.setProperty('--color-info-accent', mode.infoAccent)
    root.setProperty('--color-success', mode.success)
    root.setProperty('--color-error', mode.error)
    root.setProperty('--color-primary-text', contrastTextColor(mode.primary))
    root.setProperty('--color-primary-accent-text', contrastTextColor(mode.primaryAccent))
    root.setProperty('--color-secondary-text', contrastTextColor(mode.secondary))
    root.setProperty('--color-secondary-accent-text', contrastTextColor(mode.secondaryAccent))
    root.setProperty('--color-info-text', contrastTextColor(mode.info))
    root.setProperty('--color-info-accent-text', contrastTextColor(mode.infoAccent))
    root.setProperty('--color-success-text', contrastTextColor(mode.success))
    root.setProperty('--color-error-text', contrastTextColor(mode.error))
}

function applyFeel(feel: FeelValue) {
    if (typeof document === 'undefined') return
    document.documentElement.style.setProperty('--radius-theme', FEEL_RADIUS[feel] ?? FEEL_RADIUS[Feel.ROUNDED])
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

function initFromLocalStorage() {
    if (typeof localStorage === 'undefined') return
    const savedTheme = localStorage.getItem('theme_name')
    const savedFeel = localStorage.getItem('feel') as FeelValue | null
    const savedDarkMode = localStorage.getItem('dark_mode') as DarkModeValue | null
    if (savedTheme && THEMES[savedTheme]) activeTheme.value = savedTheme
    if (savedFeel && (savedFeel === Feel.ROUNDED || savedFeel === Feel.CORNERS)) activeFeel.value = savedFeel
    if (savedDarkMode) darkMode.value = savedDarkMode
    applyTheme(activeTheme.value)
    applyFeel(activeFeel.value)
    applyDarkMode(darkMode.value)
}

function setTheme(themeKey: string) {
    if (!THEMES[themeKey]) return
    activeTheme.value = themeKey
    applyTheme(themeKey)
    if (typeof localStorage !== 'undefined') localStorage.setItem('theme_name', themeKey)
}

function setFeel(feel: FeelValue) {
    activeFeel.value = feel
    applyFeel(feel)
    if (typeof localStorage !== 'undefined') localStorage.setItem('feel', feel)
}

function setDarkMode(mode: DarkModeValue) {
    darkMode.value = mode
    applyDarkMode(mode)
    if (typeof localStorage !== 'undefined') localStorage.setItem('dark_mode', mode)
}

export function useTheme() {
    return {
        activeTheme: readonly(activeTheme),
        activeFeel: readonly(activeFeel),
        darkMode: readonly(darkMode),
        applyTheme,
        applyFeel,
        applyDarkMode,
        initFromLocalStorage,
        setTheme,
        setFeel,
        setDarkMode,
    }
}
