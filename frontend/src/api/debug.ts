/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {ChartSpec} from '~/util/chartData'
import type {SectionFormat} from '~/util/debugLog'

/** The parts of a report the viewer shows, each behind a tab. */
export type DebugTab = 'overview' | 'charts' | 'logs' | 'exceptions' | 'configs' | 'plugins'

export type SectionKind = 'META' | 'LOG' | 'PLUGIN_LOG' | 'INTERNAL_EXCEPTION' | 'EXTERNAL_EXCEPTION' | 'CONFIG' | 'CHECK' | 'CHART'

/** The outcome of a health check the plugin ran. */
export type CheckLevel = 'OK' | 'WARN' | 'ERROR'

export interface MetaEntry {
    key: string
    value: string
}

export interface DebugSection {
    position: number
    kind: SectionKind
    name: string
    /** Characters, so the size can be shown before the content is fetched. */
    length: number
    lines: number
    /** How the content is highlighted; guessed from the name when missing. */
    format?: SectionFormat | null
    /** Set on a check. */
    level?: CheckLevel | null
    /** Set on metadata the plugin sent as pairs, shown as a table. */
    entries?: MetaEntry[] | null
    /** How often an exception occurred, with the log times of the first and the last. */
    occurrences?: number | null
    firstSeen?: string | null
    lastSeen?: string | null
    /** Set on a chart: its type, its data as the plugin sent it, and drawing hints. */
    chart?: ChartSpec | null
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

/** The latest version on the reported version's track, when the plugin is a product here. */
export interface LatestVersion {
    version: string
    outdated: boolean
}

export async function getLatestVersion(readKey: string): Promise<LatestVersion | null> {
    const response = await client.get<LatestVersion | ''>(`/api/v1/debug/${encodeURIComponent(readKey)}/latest`)
    return response.status === 200 && response.data ? response.data : null
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
