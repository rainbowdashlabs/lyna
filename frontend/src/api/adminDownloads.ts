/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export type ReleaseType = 'STABLE' | 'DEV' | 'SNAPSHOT'

export const RELEASE_TYPES: ReleaseType[] = ['STABLE', 'DEV', 'SNAPSHOT']

export interface DownloadType {
    id: number
    name: string
    description: string
    releaseType: ReleaseType
    /** The products offering a download of this type, which keep it from being deleted. */
    usedBy: string[]
}

export interface DownloadTypeEdit {
    name: string
    description: string
    releaseType: ReleaseType
}

export async function listDownloadTypes(guildId: string): Promise<DownloadType[]> {
    const {data} = await client.get<DownloadType[]>(`/api/admin/g/${guildId}/download-types`)
    return data
}

export async function createDownloadType(guildId: string, type: DownloadTypeEdit): Promise<DownloadType> {
    const {data} = await client.post<DownloadType>(`/api/admin/g/${guildId}/download-types`, type)
    return data
}

/** Renames and redescribes a type. Its release type stays what it was made as. */
export async function editDownloadType(guildId: string, typeId: number, type: DownloadTypeEdit): Promise<void> {
    await client.put(`/api/admin/g/${guildId}/download-types/${typeId}`, type)
}

export async function deleteDownloadType(guildId: string, typeId: number): Promise<void> {
    await client.delete(`/api/admin/g/${guildId}/download-types/${typeId}`)
}
