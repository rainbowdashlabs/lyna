/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface PublicTheme {
    defaultTheme: string
    allowUserTheme: boolean
    enabledThemes: string[]
    customThemeColors: string | null
}

export interface AppearanceChoice {
    theme: string | null
    darkMode: string | null
}

export async function publicTheme(): Promise<PublicTheme> {
    const {data} = await client.get<PublicTheme>('/api/theme/public')
    return data
}

export async function saveAppearance(choice: Partial<AppearanceChoice>): Promise<AppearanceChoice> {
    const {data} = await client.patch<AppearanceChoice>('/api/account/appearance', choice)
    return data
}
