/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
export const Feel = {ROUNDED: 'ROUNDED', CORNERS: 'CORNERS'} as const
export type FeelValue = (typeof Feel)[keyof typeof Feel]

export const FEEL_RADIUS: Record<FeelValue, string> = {
    ROUNDED: '0.5rem',
    CORNERS: '0.125rem',
}

export interface ModeColors {
    primary: string
    primaryAccent: string
    secondary: string
    secondaryAccent: string
    info: string
    infoAccent: string
    success: string
    error: string
}

export interface ThemeColors {
    light: ModeColors
    dark: ModeColors
    bgLight: string
    bgLightAccent: string
    bgDark: string
    bgDarkAccent: string
}

export interface ThemeDefinition {
    label: string
    colors: ThemeColors
    supportedFeels: FeelValue[]
}

export const THEMES: Record<string, ThemeDefinition> = {
    lyna: {
        label: 'Lyna',
        colors: {
            light: {
                primary: '#E92063',
                primaryAccent: '#AD1849',
                secondary: '#2CB0ED',
                secondaryAccent: '#446DF2',
                info: '#c8ab03',
                infoAccent: '#af7501',
                success: '#00C507',
                error: '#ec2929',
            },
            dark: {
                primary: '#ff4e85',
                primaryAccent: '#c81b58',
                secondary: '#4cc3f5',
                secondaryAccent: '#5b7ef5',
                info: '#e0c420',
                infoAccent: '#c89810',
                success: '#30e038',
                error: '#f05050',
            },
            bgLight: '#eceff4',
            bgLightAccent: '#d8dee9',
            bgDark: '#1E2129',
            bgDarkAccent: '#111317',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    midnight: {
        label: 'Midnight',
        colors: {
            light: {
                primary: '#3668aa',
                primaryAccent: '#0F2A99',
                secondary: '#C9A84C',
                secondaryAccent: '#A68932',
                info: '#5B8DB8',
                infoAccent: '#3D6F99',
                success: '#2E8B57',
                error: '#C0392B',
            },
            dark: {
                primary: '#5088cc',
                primaryAccent: '#3060aa',
                secondary: '#ddc060',
                secondaryAccent: '#c0a048',
                info: '#78aad0',
                infoAccent: '#5890b8',
                success: '#40a870',
                error: '#e05040',
            },
            bgLight: '#E8ECF1',
            bgLightAccent: '#CBD4DE',
            bgDark: '#0D1B2A',
            bgDarkAccent: '#071120',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
    forest: {
        label: 'Forest',
        colors: {
            light: {
                primary: '#2D6A4F',
                primaryAccent: '#1B4332',
                secondary: '#8B5E3C',
                secondaryAccent: '#6B4226',
                info: '#A3B18A',
                infoAccent: '#7F9468',
                success: '#40916C',
                error: '#C44536',
            },
            dark: {
                primary: '#48907a',
                primaryAccent: '#306858',
                secondary: '#b08060',
                secondaryAccent: '#906840',
                info: '#c0ccaa',
                infoAccent: '#a0b488',
                success: '#58b088',
                error: '#e06050',
            },
            bgLight: '#EDF2E8',
            bgLightAccent: '#D1DACA',
            bgDark: '#1A2418',
            bgDarkAccent: '#111A10',
        },
        supportedFeels: [Feel.ROUNDED, Feel.CORNERS],
    },
}

export type ThemeKey = keyof typeof THEMES

export const DarkMode = {SYSTEM: 'system', DARK: 'dark', LIGHT: 'light'} as const
export type DarkModeValue = (typeof DarkMode)[keyof typeof DarkMode]
