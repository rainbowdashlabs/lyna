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
    /** The product key type of server keys; null for upgrade tokens. */
    keyTypeId: number | null
    keyTypeName: string | null
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
    keyTypeId: number | null
    keyTypeName: string | null
    name: string
    createdAt: string
    lastUsedAt: string | null
    expiresAt: string | null
}

/** What an admin sets on a product key type. */
export interface KeyTypeDraft {
    /** How the type is named to its gateway: lower-case letters, digits and dashes. */
    slug: string
    /** What buyers see it called. */
    name: string
    /** How a key is handed to a server, with {key} where it goes; null shows the bare key. */
    command: string | null
    enabled: boolean
    /** How old a key may get before a refresh trades it for its successor. */
    refreshDays: number
    /** How long a key may go unused before it expires. */
    expiryDays: number
    /** The keys a buyer may hold before asking for more. */
    allowance: number
    /** The keys an account that only took the product's trial may hold. */
    trialAllowance: number
}

/** A kind of server key a product hands out, with the secret its gateway authenticates with. */
export interface KeyType extends KeyTypeDraft {
    id: number
    productId: number
    gatewaySecret: string
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

/** Sets the upgrade tokens, or the server keys of the key type named, allowed on top of the default. */
export async function setAllowance(
    guildId: string, accountId: number, productId: number, granted: number, keyTypeId: number | null = null): Promise<void> {
    await client.put(`${base(guildId)}/allowances`, {accountId, productId, granted, keyTypeId})
}

export async function heldKeys(guildId: string, kind: KeyKind): Promise<HeldKey[]> {
    const {data} = await client.get<HeldKey[]>(`${base(guildId)}/keys`, {params: {kind}})
    return data
}

/** Revokes a server key on one of the guild's products, whoever holds it. */
export async function revokeHeldKey(guildId: string, id: number): Promise<void> {
    await client.delete(`${base(guildId)}/keys/${id}`)
}

const keyTypes = (guildId: string) => `/api/admin/g/${guildId}`

export async function productKeyTypes(guildId: string, productId: number): Promise<KeyType[]> {
    const {data} = await client.get<KeyType[]>(`${keyTypes(guildId)}/products/${productId}/key-types`)
    return data
}

export async function createKeyType(guildId: string, productId: number, draft: KeyTypeDraft): Promise<KeyType> {
    const {data} = await client.post<KeyType>(`${keyTypes(guildId)}/products/${productId}/key-types`, draft)
    return data
}

export async function updateKeyType(guildId: string, id: number, draft: KeyTypeDraft): Promise<KeyType> {
    const {data} = await client.put<KeyType>(`${keyTypes(guildId)}/key-types/${id}`, draft)
    return data
}

/** Gives a key type a new gateway secret; the old one stops working at once. */
export async function replaceKeyTypeSecret(guildId: string, id: number): Promise<KeyType> {
    const {data} = await client.post<KeyType>(`${keyTypes(guildId)}/key-types/${id}/secret`)
    return data
}

/** Ends a key type and every key of it. */
export async function deleteKeyType(guildId: string, id: number): Promise<void> {
    await client.delete(`${keyTypes(guildId)}/key-types/${id}`)
}
