/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface TimeChannel {
    /** Text, because a Discord id is larger than a number holds exactly. */
    channelId: string
    channelName: string | null
    zone: string
    template: string
    /** What the name says right now. */
    shows: string
}

export interface RenameableChannel {
    id: string
    name: string
    type: string
}

const base = (guildId: string) => `/api/admin/g/${guildId}/time-channels`

export async function listTimeChannels(guildId: string): Promise<TimeChannel[]> {
    const {data} = await client.get<TimeChannel[]>(base(guildId))
    return data
}

/** The channels a clock can be put on; empty when no bot is connected. */
export async function listRenameableChannels(guildId: string): Promise<RenameableChannel[]> {
    const {data} = await client.get<RenameableChannel[]>(`${base(guildId)}/channels`)
    return data
}

export async function matchingZones(guildId: string, typed: string): Promise<string[]> {
    const {data} = await client.get<string[]>(`${base(guildId)}/zones`, {params: {q: typed}})
    return data
}

export async function setTimeChannel(guildId: string, channelId: string, zone: string, template: string | null): Promise<void> {
    await client.post(base(guildId), {channelId, zone, template})
}

export async function removeTimeChannel(guildId: string, channelId: string): Promise<void> {
    await client.delete(`${base(guildId)}/${channelId}`)
}
