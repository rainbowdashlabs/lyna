/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

/** One updater the account runs, and the upgrade token it holds now. */
export interface UpdateToken {
    id: number
    productId: number
    productName: string
    name: string
    /** Replaced every time the updater trades it for a download. */
    token: string
    createdAt: string
    lastUsedAt: string | null
    /** When a token this updater had already traded in was presented again, meaning somebody holds a copy. */
    lastReuseAt: string | null
}

export interface ProductAllowance {
    productId: number
    productName: string
    used: number
    allowed: number
    /** Whether a request for more is waiting on an admin. */
    pending: boolean
}

export type RequestStatus = 'PENDING' | 'APPROVED' | 'DECLINED'

export interface UpdateTokenRequest {
    id: number
    accountId: number
    account: string
    productId: number
    productName: string
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
export type UpdateTokenRefusal = 'NO_ACCESS' | 'FREE' | 'LIMIT_REACHED' | 'INVALID_NAME' | 'INVALID_REQUEST'
    | 'PENDING' | 'NOT_FOUND'

const BASE = '/api/account/update-tokens'

export async function updateTokenOverview(): Promise<UpdateTokenOverview> {
    const {data} = await client.get<UpdateTokenOverview>(BASE)
    return data
}

export async function createUpdateToken(productId: number, name: string): Promise<UpdateToken> {
    const {data} = await client.post<UpdateToken>(BASE, {productId, name})
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

export async function requestMoreUpdateTokens(productId: number, amount: number, reason: string): Promise<UpdateTokenRequest> {
    const {data} = await client.post<UpdateTokenRequest>(`${BASE}/requests`, {productId, amount, reason})
    return data
}

/** The refusal a failed call carries, or null when it failed for some other reason. */
export function refusalOf(error: unknown): UpdateTokenRefusal | null {
    const data = (error as {response?: {data?: unknown}}).response?.data
    return typeof data === 'string' && data.length > 0 ? data as UpdateTokenRefusal : null
}
