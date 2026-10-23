/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {contrastTextColor, ensureContrast} from './contrast'
import {type ModeColors, THEMES, type ThemeColors} from './themes'

/** The theme everything falls back to when nothing else names a real one. */
export const FALLBACK_THEME = 'transistor'

/** What the operator decided about how the application looks, as `/api/theme/public` serves it. */
export interface ThemePolicy {
    defaultTheme: string
    allowUserTheme: boolean
    enabledThemes: string[]
    customThemeColors: string | null
}

/**
 * The operator's own colours, laid over whichever theme is shown. Any token may be left out, and
 * each one that is given is a six-digit hex colour by the time it gets here.
 */
export interface CustomThemeColors {
    light?: Partial<ModeColors>
    dark?: Partial<ModeColors>
    bgLight?: string
    bgLightAccent?: string
    bgDark?: string
    bgDarkAccent?: string
}

export const PAGE_TOKENS = ['bgLight', 'bgLightAccent', 'bgDark', 'bgDarkAccent'] as const
export const MODE_TOKENS = [
    'primary', 'primaryAccent', 'secondary', 'secondaryAccent', 'info', 'infoAccent', 'success', 'error',
] as const
const HEX_COLOR = /^#(?:[0-9a-fA-F]{3}|[0-9a-fA-F]{6})$/

function isTheme(key: string | null | undefined): key is string {
    return !!key && Object.prototype.hasOwnProperty.call(THEMES, key)
}

/** The operator's default, or the fallback when the default names no theme this build knows. */
export function defaultTheme(policy: ThemePolicy | null): string {
    return isTheme(policy?.defaultTheme) ? policy!.defaultTheme : FALLBACK_THEME
}

/**
 * The themes somebody may pick. With the choice locked that is the default alone; otherwise it is
 * the enabled list, or the whole catalogue when the list is empty.
 */
export function offeredThemes(policy: ThemePolicy | null): string[] {
    if (policy && !policy.allowUserTheme) return [defaultTheme(policy)]
    const enabled = (policy?.enabledThemes ?? []).filter(isTheme)
    return enabled.length > 0 ? enabled : Object.keys(THEMES)
}

/**
 * The theme somebody actually sees: their own choice where the operator offers it, the operator's
 * default everywhere else.
 */
export function effectiveTheme(policy: ThemePolicy | null, chosen: string | null | undefined): string {
    if (isTheme(chosen) && offeredThemes(policy).includes(chosen)) return chosen
    return defaultTheme(policy)
}

function expandHex(value: string): string {
    if (value.length !== 4) return value.toLowerCase()
    return ('#' + [...value.slice(1)].map(digit => digit + digit).join('')).toLowerCase()
}

function pickColors<K extends string>(source: unknown, keys: readonly K[]): Partial<Record<K, string>> {
    const picked: Partial<Record<K, string>> = {}
    if (!source || typeof source !== 'object') return picked
    const record = source as Record<string, unknown>
    for (const key of keys) {
        const value = record[key]
        if (typeof value === 'string' && HEX_COLOR.test(value)) picked[key] = expandHex(value)
    }
    return picked
}

/**
 * Reads the operator's custom colours. The backend refuses anything else on save, and anything that
 * is not a known token holding a hex colour is still left out here, because these values end up in a
 * style block.
 */
export function parseCustomColors(json: string | null | undefined): CustomThemeColors | null {
    if (!json) return null
    let parsed: unknown
    try {
        parsed = JSON.parse(json)
    } catch {
        return null
    }
    if (!parsed || typeof parsed !== 'object' || Array.isArray(parsed)) return null
    const record = parsed as Record<string, unknown>
    return {
        ...pickColors(record, PAGE_TOKENS),
        light: pickColors(record.light, MODE_TOKENS),
        dark: pickColors(record.dark, MODE_TOKENS),
    }
}

/** A theme's colours with the operator's custom colours laid over them. */
export function themeColors(theme: string, custom: CustomThemeColors | null): ThemeColors {
    const base = THEMES[theme]?.colors ?? THEMES[FALLBACK_THEME]!.colors
    if (!custom) return base
    return {
        bgLight: custom.bgLight ?? base.bgLight,
        bgLightAccent: custom.bgLightAccent ?? base.bgLightAccent,
        bgDark: custom.bgDark ?? base.bgDark,
        bgDarkAccent: custom.bgDarkAccent ?? base.bgDarkAccent,
        light: {...base.light, ...custom.light},
        dark: {...base.dark, ...custom.dark},
    }
}

/** The custom properties that hold in either mode. */
export function pageVariables(colors: ThemeColors): Record<string, string> {
    return {
        '--color-bg-light': colors.bgLight,
        '--color-bg-light-accent': colors.bgLightAccent,
        '--color-bg-dark': colors.bgDark,
        '--color-bg-dark-accent': colors.bgDarkAccent,
    }
}

/** The custom properties of one mode, with the text and badge colours worked out to stay legible. */
export function modeVariables(mode: ModeColors, pageBg: string): Record<string, string> {
    return {
        '--color-primary': mode.primary,
        '--color-primary-accent': mode.primaryAccent,
        '--color-secondary': mode.secondary,
        '--color-secondary-accent': mode.secondaryAccent,
        '--color-info': mode.info,
        '--color-info-accent': mode.infoAccent,
        '--color-success': mode.success,
        '--color-error': mode.error,
        '--color-primary-text': contrastTextColor(mode.primary),
        '--color-primary-accent-text': contrastTextColor(mode.primaryAccent),
        '--color-secondary-text': contrastTextColor(mode.secondary),
        '--color-secondary-accent-text': contrastTextColor(mode.secondaryAccent),
        '--color-info-text': contrastTextColor(mode.info),
        '--color-info-accent-text': contrastTextColor(mode.infoAccent),
        '--color-success-text': contrastTextColor(mode.success),
        '--color-error-text': contrastTextColor(mode.error),
        '--color-primary-badge': ensureContrast(mode.primaryAccent, pageBg),
        '--color-secondary-badge': ensureContrast(mode.secondaryAccent, pageBg),
        '--color-info-badge': ensureContrast(mode.infoAccent, pageBg),
        '--color-success-badge': ensureContrast(mode.success, pageBg),
        '--color-error-badge': ensureContrast(mode.error, pageBg),
    }
}
