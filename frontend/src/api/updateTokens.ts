/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** What a server key is for: an updater's upgrade token, or a server's key to the hosted dashboard. */
export type KeyKind = 'UPDATE' | 'DASHBOARD'

/** One server the account runs a product on, and the key of one kind it holds now. */
export interface UpdateToken {
    id: number
    productId: number
    productName: string
    kind: KeyKind
    name: string
    /** Replaced on every trade of an upgrade token, and on every refresh that rotates a dashboard key. */
    token: string
    createdAt: string
    lastUsedAt: string | null
    /** When a key this server had already given up was presented again, meaning somebody holds a copy. */
    lastReuseAt: string | null
    /** When the current key was issued. */
    rotatedAt: string | null
    /** When the server holding a dashboard key last asked to refresh it. */
    refreshedAt: string | null
    /** When a refresh will trade a dashboard key for its successor; null for an upgrade token. */
    refreshAt: string | null
    /** When a dashboard key expires unless it is used before; null for an upgrade token. */
    expiresAt: string | null
}

export interface ProductAllowance {
    productId: number
    productName: string
    kind: KeyKind
    used: number
    allowed: number
    /** Whether a request for more is waiting on an admin. */
    pending: boolean
    /** Whether the account only took the product's trial: one dashboard key, and no request for more. */
    trial: boolean
}

export type RequestStatus = 'PENDING' | 'APPROVED' | 'DECLINED'

export interface UpdateTokenRequest {
    id: number
    accountId: number
    account: string
    productId: number
    productName: string
    kind: KeyKind
    amount: number
    reason: string
    status: RequestStatus
    createdAt: string
    decidedAt: string | null
}

export interface UpdateTokenOverview {
    tokens: UpdateToken[]
    products: ProductAllowance[]
    requests: UpdateTokenRequest[]
}

/** Why the backend refused, as it names it. */
export type UpdateTokenRefusal = 'NO_ACCESS' | 'FREE' | 'NOT_OFFERED' | 'LIMIT_REACHED' | 'INVALID_NAME'
    | 'INVALID_REQUEST' | 'PENDING' | 'NOT_FOUND'

const BASE = '/api/account/update-tokens'

export async function updateTokenOverview(): Promise<UpdateTokenOverview> {
    const {data} = await client.get<UpdateTokenOverview>(BASE)
    return data
}

export async function createUpdateToken(productId: number, name: string, kind: KeyKind = 'UPDATE'): Promise<UpdateToken> {
    const {data} = await client.post<UpdateToken>(BASE, {productId, name, kind})
    return data
}

export async function renameUpdateToken(id: number, name: string): Promise<UpdateToken> {
    const {data} = await client.put<UpdateToken>(`${BASE}/${id}`, {name})
    return data
}

export async function reissueUpdateToken(id: number): Promise<UpdateToken> {
    const {data} = await client.post<UpdateToken>(`${BASE}/${id}/reissue`)
    return data
}

export async function revokeUpdateToken(id: number): Promise<void> {
    await client.delete(`${BASE}/${id}`)
}

export async function requestMoreUpdateTokens(
    productId: number, amount: number, reason: string, kind: KeyKind = 'UPDATE'): Promise<UpdateTokenRequest> {
    const {data} = await client.post<UpdateTokenRequest>(`${BASE}/requests`, {productId, amount, reason, kind})
    return data
}

/** The refusal a failed call carries, or null when it failed for some other reason. */
export function refusalOf(error: unknown): UpdateTokenRefusal | null {
    const data = (error as {response?: {data?: unknown}}).response?.data
    return typeof data === 'string' && data.length > 0 ? data as UpdateTokenRefusal : null
}
