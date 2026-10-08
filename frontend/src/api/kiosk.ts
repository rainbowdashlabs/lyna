/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface KioskProduct {
    id: number
    guildId: string
    name: string
    url: string | null
    iconUrl: string | null
    free: boolean
    purchaseUrl: string | null
    /** Whether this visitor already holds a license covering the product. */
    entitled: boolean
    /** The newest stable version, or null when there is none. */
    latestVersion: string | null
    /** When the newest stable version was published. */
    updatedAt: string | null
}

/**
 * A product on its own page.
 *
 * <p>The catalogue does not carry the description, because a grid of tiles has no room for it and
 * every tile would pay for prose nobody reads.
 */
export interface KioskProductDetail extends KioskProduct {
    /** Markdown: what the operator wrote, or the project's GitHub README. Rendered where it is shown. */
    description: string | null
    /** Which of the two the description is. */
    pageSource: 'CUSTOM' | 'README'
    /** The README's page on GitHub, when the description is the README. */
    readmeUrl: string | null
    /** Whether somebody without a license may download it once to try it. */
    trial: boolean
}

/**
 * What a tile offers this visitor: download it, buy it, or say where to buy it is not known.
 */
export function callToAction(product: KioskProduct): 'download' | 'buy' | 'unavailable' {
    if (product.free || product.entitled) return 'download'
    return product.purchaseUrl ? 'buy' : 'unavailable'
}

export interface ReleaseTypeEntry {
    id: string
    description: string | null
    /** Whether the reader may download builds of it. Anybody may see which exist. */
    downloadable: boolean
}

export interface VersionEntry {
    version: string
    publishedAt: string
    downloadTypeIds: number[]
}

export interface DownloadTypeEntry {
    id: number
    name: string
    description: string | null
}

export interface IssuedDownload {
    url: string
    filename: string
    sizeBytes: number | null
    expiresAt: string
}

export async function listProducts(): Promise<KioskProduct[]> {
    const {data} = await client.get<KioskProduct[]>('/api/v1/products')
    return data
}

/**
 * What the instance says about itself: the build it is running, and the links it was configured with.
 *
 * <p>Public, because the footer is drawn where nobody has signed in.
 */
export interface InstanceInfo {
    /** The version, carrying the commit and the build time when CI made it. */
    version: string
    website: string | null
    discord: string | null
    faq: string | null
    terms: string | null
}

export async function getInstanceInfo(): Promise<InstanceInfo> {
    const {data} = await client.get<InstanceInfo>('/api/v1/instance')
    return data
}

export async function getProduct(productId: number): Promise<KioskProductDetail> {
    const {data} = await client.get<KioskProductDetail>(`/api/v1/products/${productId}`)
    return data
}

export async function listReleaseTypes(productId: number): Promise<ReleaseTypeEntry[]> {
    const {data} = await client.get<ReleaseTypeEntry[]>(`/api/v1/products/${productId}/release-types`)
    return data
}

export async function listVersions(productId: number, releaseType: string, limit = 25): Promise<VersionEntry[]> {
    const {data} = await client.get<VersionEntry[]>(
        `/api/v1/products/${productId}/release-types/${releaseType}/versions?limit=${limit}`)
    return data
}

export async function listDownloadTypes(productId: number, version: string): Promise<DownloadTypeEntry[]> {
    const {data} = await client.get<DownloadTypeEntry[]>(
        `/api/v1/products/${productId}/versions/${encodeURIComponent(version)}/download-types`)
    return data
}

export async function issueDownload(
    productId: number, version: string, downloadTypeId: number): Promise<IssuedDownload> {
    const {data} = await client.post<IssuedDownload>(
        `/api/v1/products/${productId}/versions/${encodeURIComponent(version)}/downloads/${downloadTypeId}/issue`)
    return data
}

export type TrialReason = 'ELIGIBLE' | 'NOT_OFFERED' | 'SPENT' | 'GUILD_MEMBERSHIP_TOO_NEW' | 'DISCORD_ACCOUNT_TOO_NEW'
    | 'ACCOUNT_TOO_NEW' | 'NO_VERIFIED_EMAIL' | 'DISPOSABLE_EMAIL'

export interface TrialStatus {
    reason: TrialReason
    /** How long until a refusal for age passes. */
    waitSeconds: number
    /** The stable builds a trial may take, when it may be taken. */
    downloads: {typeId: number, name: string, description: string, version: string}[]
}

/** Whether the signed-in account may take the product's trial, and what it could download. */
export async function getTrialStatus(productId: number): Promise<TrialStatus> {
    const {data} = await client.get<TrialStatus>(`/api/v1/products/${productId}/trial`)
    return data
}

/** Spends the trial on the newest stable build of a download type and returns its one-time link. */
export async function issueTrial(productId: number, downloadTypeId: number): Promise<IssuedDownload> {
    const {data} = await client.post<IssuedDownload>(`/api/v1/products/${productId}/trial/${downloadTypeId}/issue`)
    return data
}
