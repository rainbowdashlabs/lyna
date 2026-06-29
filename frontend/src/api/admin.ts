import client from './client'

export interface AdminGuild {
    id: string
    name: string
    iconUrl: string | null
    role: 'guild_admin' | 'operator'
}

export interface ProductSummary {
    id: number
    name: string
    url: string | null
    roleId: number
}

export interface CreateProductPayload {
    name: string
    url: string | null
    roleId: string
    free: boolean
    trial: boolean
}

export interface LicenseSummary {
    id: number
    productId: number
    productName: string
    identifier: string
    owner: number
    shareeCount: number
}

export interface LicenseDetail {
    id: number
    productId: number
    productName: string
    identifier: string
    key: string
    owner: number
    sharees: number[]
}

export interface RegistrationInfo {
    discordId: number
    memberName: string | null
}

export async function listAdminGuilds(): Promise<AdminGuild[]> {
    const {data} = await client.get<AdminGuild[]>('/api/admin/guilds')
    return data
}

export async function listGuildProducts(guildId: string): Promise<ProductSummary[]> {
    const {data} = await client.get<ProductSummary[]>(`/api/admin/g/${guildId}/products`)
    return data
}

export async function createGuildProduct(guildId: string, payload: CreateProductPayload): Promise<ProductSummary> {
    const {data} = await client.post<ProductSummary>(`/api/admin/g/${guildId}/products`, payload)
    return data
}

export async function listGuildLicenses(guildId: string): Promise<LicenseSummary[]> {
    const {data} = await client.get<LicenseSummary[]>(`/api/admin/g/${guildId}/licenses`)
    return data
}

export async function createGuildLicense(guildId: string, productId: number, identifier: string): Promise<LicenseDetail> {
    const {data} = await client.post<LicenseDetail>(`/api/admin/g/${guildId}/licenses`, {productId, identifier})
    return data
}

export async function lookupRegistration(guildId: string, discordId: string): Promise<RegistrationInfo> {
    const {data} = await client.get<RegistrationInfo>(`/api/admin/g/${guildId}/registrations/${discordId}`)
    return data
}

export interface InstanceAppearance {
    defaultTheme: string
    defaultFeel: string
    lockFeel: boolean
    allowUserTheme: boolean
    allowUserFeel: boolean
    enabledThemes: string[]
    customThemeColorsJson: string | null
}

export interface SystemInfo {
    version: string
    guildCount: number
}

export async function getInstanceAppearance(): Promise<InstanceAppearance> {
    const {data} = await client.get<InstanceAppearance>('/api/admin/instance/appearance')
    return data
}

export async function updateInstanceAppearance(payload: InstanceAppearance): Promise<void> {
    await client.put('/api/admin/instance/appearance', payload)
}

export async function getInstanceSystem(): Promise<SystemInfo> {
    const {data} = await client.get<SystemInfo>('/api/admin/instance/system')
    return data
}
