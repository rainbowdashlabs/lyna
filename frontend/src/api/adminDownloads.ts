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

export interface ProductDownload {
    typeId: number
    typeName: string
    releaseType: ReleaseType
    repository: string
    groupId: string
    artifactId: string
    classifier: string | null
    /** The newest version Nexus holds for these coordinates; null when it holds none or was unreachable. */
    latestVersion: string | null
}

export interface DownloadEdit {
    typeId: number | null
    repository: string
    groupId: string
    artifactId: string
    classifier: string | null
}

export interface RoleAccess {
    /** Text, because a Discord id is larger than a number holds exactly. */
    roleId: string
    roleName: string | null
    releaseType: ReleaseType
}

export interface GuildRole {
    id: string
    name: string
}

const product = (guildId: string, productId: number) => `/api/admin/g/${guildId}/products/${productId}`

export async function listProductDownloads(guildId: string, productId: number): Promise<ProductDownload[]> {
    const {data} = await client.get<ProductDownload[]>(`${product(guildId, productId)}/downloads`)
    return data
}

export async function createProductDownload(guildId: string, productId: number, edit: DownloadEdit): Promise<ProductDownload> {
    const {data} = await client.post<ProductDownload>(`${product(guildId, productId)}/downloads`, edit)
    return data
}

export async function editProductDownload(guildId: string, productId: number, typeId: number, edit: DownloadEdit): Promise<ProductDownload> {
    const {data} = await client.put<ProductDownload>(`${product(guildId, productId)}/downloads/${typeId}`, edit)
    return data
}

export async function deleteProductDownload(guildId: string, productId: number, typeId: number): Promise<void> {
    await client.delete(`${product(guildId, productId)}/downloads/${typeId}`)
}

export async function listRoleAccess(guildId: string, productId: number): Promise<RoleAccess[]> {
    const {data} = await client.get<RoleAccess[]>(`${product(guildId, productId)}/role-access`)
    return data
}

export async function grantRoleAccess(guildId: string, productId: number, roleId: string, releaseType: ReleaseType): Promise<void> {
    await client.post(`${product(guildId, productId)}/role-access`, {roleId, releaseType})
}

export async function revokeRoleAccess(guildId: string, productId: number, roleId: string, releaseType: ReleaseType): Promise<void> {
    await client.delete(`${product(guildId, productId)}/role-access/${roleId}/${releaseType}`)
}

/** The guild's roles, for picking one. Empty when no bot is connected; a role is then typed by id. */
export async function listGuildRoles(guildId: string): Promise<GuildRole[]> {
    const {data} = await client.get<GuildRole[]>(`/api/admin/g/${guildId}/roles`)
    return data
}
