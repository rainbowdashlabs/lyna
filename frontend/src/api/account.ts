import client from './client'

export interface LoginPayload {
    email: string
    password: string
}

export interface AccountInfo {
    id: number
    email: string | null
    emailVerified: boolean
    /** An address a confirmation link is outstanding for, which is not yet the account's. */
    pendingEmail: string | null
    hasPassword: boolean
    discordId: string | null
    discordLinkedAt: string | null
    theme: string | null
    feel: string | null
    darkMode: string | null
}

export interface DownloadRow {
    id: number
    accountId: number | null
    discordId: string | null
    licenseId: number | null
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

export interface ProductOption {
    id: number
    name: string
}

export interface DownloadPage {
    rows: DownloadRow[]
    totalRows: number
    page: number
    pageSize: number
    products: ProductOption[]
}

export interface DownloadFilters {
    from?: string
    to?: string
    product?: number | null
    source?: string | null
    license?: number | null
    page?: number
    pageSize?: number
}

export async function listDownloads(filters: DownloadFilters = {}): Promise<DownloadPage> {
    const params = new URLSearchParams()
    for (const [key, value] of Object.entries(filters)) {
        if (value !== null && value !== undefined && value !== '') params.set(key, String(value))
    }
    const {data} = await client.get<DownloadPage>(`/api/account/downloads?${params.toString()}`)
    return data
}

export async function requestPasswordReset(email: string): Promise<void> {
    await client.post('/api/auth/password/reset/request', {email})
}

export async function confirmPasswordReset(token: string, newPassword: string): Promise<void> {
    await client.post('/api/auth/password/reset/confirm', {token, newPassword})
}

export interface LicenseView {
    id: number
    guildId: string
    productId: number
    productName: string
    productUrl: string | null
    userIdentifier: string
    releaseTypes: string[]
    role: 'owner' | 'sharee'
    ownerDiscordId: string
    shareesUsed: number
    shareesCap: number
}

export interface LicenseList {
    owned: LicenseView[]
    shared: LicenseView[]
}

export interface LicenseDetail {
    license: LicenseView
    key: string | null
    sharees: string[]
    recentDownloads: DownloadRow[]
}

export async function listLicenses(): Promise<LicenseList> {
    const {data} = await client.get<LicenseList>('/api/account/licenses')
    return data
}

export async function licenseDetail(id: number): Promise<LicenseDetail> {
    const {data} = await client.get<LicenseDetail>(`/api/account/licenses/${id}`)
    return data
}

export async function addSharee(id: number, subject: string): Promise<void> {
    await client.post(`/api/account/licenses/${id}/sharees`, {subject})
}

export async function removeSharee(id: number, discordId: string): Promise<void> {
    await client.delete(`/api/account/licenses/${id}/sharees/${encodeURIComponent(discordId)}`)
}

export async function changeEmail(newEmail: string): Promise<void> {
    await client.post('/api/account/email/change', {newEmail})
}

export async function resendVerification(): Promise<void> {
    await client.post('/api/account/email/resend-verification')
}

export async function verifyEmail(token: string): Promise<void> {
    await client.post('/api/auth/email/verify', {token})
}
