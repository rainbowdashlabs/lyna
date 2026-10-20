/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'
import type {UpdateTokenRequest} from './updateTokens'

/** An account allowed more upgrade tokens on a product than the default. */
export interface RaisedAllowance {
    accountId: number
    account: string
    productId: number
    productName: string
    /** Tokens allowed on top of the default. */
    granted: number
    /** Tokens the account holds now. */
    used: number
}

const base = (guildId: string) => `/api/admin/g/${guildId}/update-tokens`

export async function pendingUpdateTokenRequests(guildId: string): Promise<UpdateTokenRequest[]> {
    const {data} = await client.get<UpdateTokenRequest[]>(`${base(guildId)}/requests`)
    return data
}

export async function decideUpdateTokenRequest(guildId: string, requestId: number, approve: boolean): Promise<void> {
    await client.post(`${base(guildId)}/requests/${requestId}/${approve ? 'approve' : 'decline'}`)
}

export async function raisedAllowances(guildId: string): Promise<RaisedAllowance[]> {
    const {data} = await client.get<RaisedAllowance[]>(`${base(guildId)}/allowances`)
    return data
}

export async function setAllowance(guildId: string, accountId: number, productId: number, granted: number): Promise<void> {
    await client.put(`${base(guildId)}/allowances`, {accountId, productId, granted})
}
