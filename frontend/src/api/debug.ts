/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** The parts of a report the viewer shows, each behind a tab. */
export type DebugTab = 'overview' | 'logs' | 'exceptions' | 'configs' | 'plugins'

export type SectionKind = 'META' | 'LOG' | 'PLUGIN_LOG' | 'INTERNAL_EXCEPTION' | 'EXTERNAL_EXCEPTION' | 'CONFIG'

export interface DebugSection {
    position: number
    kind: SectionKind
    name: string
    /** Characters, so the size can be shown before the content is fetched. */
    length: number
    lines: number
}

export interface PluginMeta {
    name: string
    version?: string
    enabled?: boolean
    main?: string
    authors?: string[]
    loadBefore?: string[]
    dependencies?: string[]
    softDependencies?: string[]
    provides?: string[]
}

export interface ServerMeta {
    version?: string
    currentPlayers?: number
    loadedWorlds?: string[]
    plugins?: PluginMeta[]
}

export interface DebugReport {
    pluginName: string
    pluginVersion: string
    created: string
    expires: string
    /** As the plugin uploaded it; older clients send fewer fields. */
    pluginMeta: PluginMeta
    serverMeta: ServerMeta
    sections: DebugSection[]
}

export async function getDebugReport(readKey: string): Promise<DebugReport> {
    const {data} = await client.get<DebugReport>(`/api/v1/debug/${encodeURIComponent(readKey)}`)
    return data
}

export async function getDebugSection(readKey: string, position: number): Promise<string> {
    const {data} = await client.get<string>(
        `/api/v1/debug/${encodeURIComponent(readKey)}/sections/${position}`,
        {responseType: 'text', transformResponse: [raw => raw]},
    )
    return data
}

export async function deleteDebugReport(deleteKey: string): Promise<void> {
    await client.delete(`/api/v1/debug/${encodeURIComponent(deleteKey)}`)
}
