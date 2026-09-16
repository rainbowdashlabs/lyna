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
    free: boolean
    iconUrl: string | null
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
    sharees: string[]
}

export interface RegistrationInfo {
    discordId: number
    memberName: string | null
    ownedLicenses: LicenseSummary[]
    sharedLicenses: LicenseSummary[]
}

export async function listAdminGuilds(): Promise<AdminGuild[]> {
    const {data} = await client.get<AdminGuild[]>('/api/admin/guilds')
    return data
}

export async function listGuildProducts(guildId: string): Promise<ProductSummary[]> {
    const {data} = await client.get<ProductSummary[]>(`/api/admin/g/${guildId}/products`)
    return data
}

export async function setProductIcon(guildId: string, productId: number, iconUrl: string): Promise<void> {
    await client.put(`/api/admin/g/${guildId}/products/${productId}/icon`, {iconUrl})
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
    allowUserTheme: boolean
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

export interface GuildSettings {
    shares: number
    trialServerMinutes: number
    trialAccountMinutes: number
    /** The role whose members administer this guild, or null when only MANAGE_SERVER does. */
    adminRoleId: string | null
}

export interface InstanceOperator {
    discordId: string
    addedBy: string | null
    addedAt: string | null
    /** Set in the config file rather than granted here, so it cannot be withdrawn here. */
    configured: boolean
}

export type MailBlock =
    | {type: 'heading'; text: string}
    | {type: 'paragraph'; text: string}
    | {type: 'key'; label: string}
    | {type: 'button'; label: string; url: string}
    | {type: 'list'; items: string[]}
    | {type: 'divider'}
    | {type: 'raw'; html: string}

export interface MailingTemplate {
    id: number
    productId: number
    productName: string
    name: string
    /** The mail as its operator composed it, or null for one written before blocks existed. */
    blocks: string | null
    mailText: string | null
}

export async function listMailings(guildId: string): Promise<MailingTemplate[]> {
    const {data} = await client.get<MailingTemplate[]>(`/api/admin/g/${guildId}/mailing`)
    return data
}

export async function saveMailingBlocks(guildId: string, id: number, blocks: MailBlock[]): Promise<void> {
    await client.put(`/api/admin/g/${guildId}/mailing/${id}`, {blocks: JSON.stringify(blocks)})
}

export async function previewMailing(guildId: string, id: number, blocks: MailBlock[]): Promise<string> {
    const {data} = await client.post<string>(
        `/api/admin/g/${guildId}/mailing/${id}/preview`, {blocks: JSON.stringify(blocks)})
    return data
}

export async function sendTestMail(guildId: string, id: number, address: string): Promise<void> {
    await client.post(`/api/admin/g/${guildId}/mailing/${id}/test`, {address})
}

export interface KofiMapping {
    linkCode: string
    productId: number
    productName: string
}

export interface TrialInfo {
    serverMinutes: number
    accountMinutes: number
    products: ProductSummary[]
}

export async function getGuildSettings(guildId: string): Promise<GuildSettings> {
    const {data} = await client.get<GuildSettings>(`/api/admin/g/${guildId}/settings`)
    return data
}

export async function updateGuildSettings(guildId: string, payload: GuildSettings): Promise<void> {
    await client.put(`/api/admin/g/${guildId}/settings`, payload)
}

export async function listKofi(guildId: string): Promise<KofiMapping[]> {
    const {data} = await client.get<KofiMapping[]>(`/api/admin/g/${guildId}/kofi`)
    return data
}

export async function createKofi(guildId: string, linkCode: string, productId: number): Promise<void> {
    await client.post(`/api/admin/g/${guildId}/kofi`, {linkCode, productId})
}

export async function getTrialInfo(guildId: string): Promise<TrialInfo> {
    const {data} = await client.get<TrialInfo>(`/api/admin/g/${guildId}/trial`)
    return data
}

export async function listOperators(): Promise<InstanceOperator[]> {
    const {data} = await client.get<InstanceOperator[]>('/api/admin/instance/operators')
    return data
}

export async function addOperator(discordId: string): Promise<InstanceOperator> {
    const {data} = await client.post<InstanceOperator>('/api/admin/instance/operators', {discordId})
    return data
}

export async function removeOperator(discordId: string): Promise<void> {
    await client.delete(`/api/admin/instance/operators/${encodeURIComponent(discordId)}`)
}
