/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import client from './client'

export interface DemoAccount {
    email: string
    /** A short name for the part this account plays, e.g. `operator`. */
    role: string
    description: string
}

/**
 * The accounts a demo instance offers.
 *
 * <p>A real instance answers 404 here, which is how the page knows not to offer any of it. The
 * caller treats that as "not a demo" rather than as an error.
 */
export async function demoAccounts(): Promise<DemoAccount[] | null> {
    try {
        const {data} = await client.get<DemoAccount[]>('/api/v1/demo/accounts')
        return data
    } catch {
        return null
    }
}

export async function demoLogin(email: string): Promise<{token: string}> {
    const {data} = await client.post<{token: string}>('/api/v1/demo/login', {email})
    return data
}
