/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {KeyKind, UpdateTokenRequest} from './updateTokens'

/** An account allowed more keys of one kind on a product than the default. */
export interface RaisedAllowance {
    accountId: number
    account: string
    productId: number
    productName: string
    kind: KeyKind
    /** Keys allowed on top of the default. */
    granted: number
    /** Keys of that kind the account holds now. */
    used: number
}

/** A key as an admin sees it: whose it is and how it is used, never the key itself. */
export interface HeldKey {
    id: number
    accountId: number
    account: string
    productId: number
    productName: string
    kind: KeyKind
    name: string
    createdAt: string
    lastUsedAt: string | null
    expiresAt: string | null
}

/** How a product hands out dashboard keys. */
export interface DashboardSettings {
    enabled: boolean
    /** How old a key may get before a refresh trades it for its successor. */
    refreshDays: number
    /** How long a key may go unused before it expires. */
    expiryDays: number
    /** The keys a buyer may hold before asking for more. */
    allowance: number
}

const base = (guildId: string) => `/api/admin/g/${guildId}/update-tokens`

export async function pendingUpdateTokenRequests(guildId: string, kind: KeyKind = 'UPDATE'): Promise<UpdateTokenRequest[]> {
    const {data} = await client.get<UpdateTokenRequest[]>(`${base(guildId)}/requests`, {params: {kind}})
    return data
}

export async function decideUpdateTokenRequest(guildId: string, requestId: number, approve: boolean): Promise<void> {
    await client.post(`${base(guildId)}/requests/${requestId}/${approve ? 'approve' : 'decline'}`)
}

export async function raisedAllowances(guildId: string, kind: KeyKind = 'UPDATE'): Promise<RaisedAllowance[]> {
    const {data} = await client.get<RaisedAllowance[]>(`${base(guildId)}/allowances`, {params: {kind}})
    return data
}

export async function setAllowance(
    guildId: string, accountId: number, productId: number, granted: number, kind: KeyKind = 'UPDATE'): Promise<void> {
    await client.put(`${base(guildId)}/allowances`, {accountId, productId, granted, kind})
}

export async function heldKeys(guildId: string, kind: KeyKind): Promise<HeldKey[]> {
    const {data} = await client.get<HeldKey[]>(`${base(guildId)}/keys`, {params: {kind}})
    return data
}

/** Revokes a dashboard key on one of the guild's products, whoever holds it. */
export async function revokeHeldKey(guildId: string, id: number): Promise<void> {
    await client.delete(`${base(guildId)}/keys/${id}`)
}

export async function dashboardSettings(guildId: string, productId: number): Promise<DashboardSettings> {
    const {data} = await client.get<DashboardSettings>(`/api/admin/g/${guildId}/products/${productId}/dashboard-keys`)
    return data
}

export async function setDashboardSettings(guildId: string, productId: number, settings: DashboardSettings): Promise<void> {
    await client.put(`/api/admin/g/${guildId}/products/${productId}/dashboard-keys`, settings)
}
