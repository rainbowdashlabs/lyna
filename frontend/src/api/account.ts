import client from './client'

export interface LoginPayload {
    email: string
    password: string
}

export interface AccountInfo {
    id: number
    email: string | null
    hasPassword: boolean
    discordId: string | null
    discordLinkedAt: string | null
    theme: string | null
    feel: string | null
    darkMode: string | null
}

export interface DownloadRow {
    id: number
    productId: number
    productName: string
    downloadId: number
    version: string
    source: string
    downloadedAt: string
}

export interface Overview {
    account: AccountInfo
    activeSessions: number
    lastSignInAt: string | null
    recentDownloads: DownloadRow[]
}

export interface SessionRow {
    jti: string
    issuedAt: string
    lastSeenAt: string | null
    userAgent: string | null
    current: boolean
}

export async function login(payload: LoginPayload): Promise<{token: string}> {
    const {data} = await client.post('/api/auth/login', payload)
    return data
}

export async function signup(payload: LoginPayload): Promise<{token: string}> {
    const {data} = await client.post('/api/auth/signup', payload)
    return data
}

export async function logout(): Promise<void> {
    await client.post('/api/auth/logout')
}

export async function overview(): Promise<Overview> {
    const {data} = await client.get<Overview>('/api/account')
    return data
}

export async function changePassword(currentPassword: string | null, newPassword: string): Promise<void> {
    await client.post('/api/account/password', {currentPassword, newPassword})
}

export async function listSessions(): Promise<SessionRow[]> {
    const {data} = await client.get<SessionRow[]>('/api/account/sessions')
    return data
}

export async function revokeSession(jti: string): Promise<void> {
    await client.delete(`/api/account/sessions/${encodeURIComponent(jti)}`)
}

export async function endOtherSessions(): Promise<void> {
    await client.delete('/api/account/sessions')
}

export async function unlinkDiscord(): Promise<void> {
    await client.delete('/api/account/discord')
}

export async function deleteAccount(confirmEmail: string): Promise<void> {
    await client.delete('/api/account', {data: {confirmEmail}})
}

export async function listDownloads(limit = 25): Promise<DownloadRow[]> {
    const {data} = await client.get<DownloadRow[]>(`/api/account/downloads?limit=${limit}`)
    return data
}
