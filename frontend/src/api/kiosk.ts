import client from './client'

export interface KioskProduct {
    id: number
    name: string
    url: string | null
}

export interface ReleaseEntry {
    id: number
    name: string
    description?: string
}

export interface VersionEntry {
    id: string
    version: string
    published: number
}

export async function listProducts(): Promise<KioskProduct[]> {
    const {data} = await client.get<KioskProduct[]>('/api/v1/products')
    return data
}

export async function listReleaseTypes(productId: number): Promise<ReleaseEntry[]> {
    const {data} = await client.get<ReleaseEntry[]>(`/api/v1/releases/${productId}`)
    return data
}

export async function listVersions(productId: number, releaseTypeId: number): Promise<VersionEntry[]> {
    const {data} = await client.get<VersionEntry[]>(`/api/v1/releases/${productId}/${releaseTypeId}`)
    return data
}

export function directDownloadUrl(productId: number, releaseTypeId: number, version: string): string {
    return `/api/v1/download/direct/${productId}/${releaseTypeId}/${version}`
}
