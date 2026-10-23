/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface DiscordRole {
    id: string
    name: string
}

export type ChannelKind = 'TEXT' | 'NEWS' | 'FORUM' | 'THREAD' | 'VOICE' | 'STAGE' | 'CATEGORY'

export interface DiscordChannel {
    id: string
    name: string
    kind: ChannelKind
    /** The category a channel is in, or the channel a thread is in, by id. */
    parent: string | null
    /** Whether it can be picked, rather than only holding what can. */
    selectable: boolean
}

/** What a picker offers. Without a bot `connected` is false and the list says nothing. */
export interface Directory<T> {
    connected: boolean
    entries: T[]
}

/** Where a channel is picked for: a post, or a clock in its name. */
export type ChannelPurpose = 'posting' | 'renaming'

const asked = new Map<string, Promise<Directory<unknown>>>()

/**
 * Asks once per guild and purpose while the page is open: a form with three pickers would otherwise
 * ask Discord three times for the same list.
 */
function once<T>(key: string, ask: () => Promise<Directory<T>>): Promise<Directory<T>> {
    let pending = asked.get(key) as Promise<Directory<T>> | undefined
    if (!pending) {
        pending = ask().catch(e => {
            asked.delete(key)
            throw e
        })
        asked.set(key, pending)
    }
    return pending
}

/** Forgets what was asked, for a page that changed something on Discord's side. */
export function forgetDirectory(): void {
    asked.clear()
}

export function listRoles(guildId: string): Promise<Directory<DiscordRole>> {
    return once(`${guildId}:roles`, async () => {
        const {data} = await client.get<{connected: boolean, roles: DiscordRole[]}>(`/api/admin/g/${guildId}/roles`)
        return {connected: data.connected, entries: data.roles}
    })
}

export function listChannels(guildId: string, purpose: ChannelPurpose): Promise<Directory<DiscordChannel>> {
    return once(`${guildId}:channels:${purpose}`, async () => {
        const {data} = await client.get<{connected: boolean, channels: DiscordChannel[]}>(
            `/api/admin/g/${guildId}/channels`, {params: {for: purpose}})
        return {connected: data.connected, entries: data.channels}
    })
}
