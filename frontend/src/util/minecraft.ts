/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import type {VersionEntry, VersionManifest} from '~/api/kiosk'

function number(part: string | undefined): number {
    const digits = /^\d+/.exec(part ?? '0')
    return digits ? Number(digits[0]) : 0
}

/**
 * Compares a Minecraft version with a bound over as many parts as the bound has, so `1.21` covers
 * `1.21.4`. The same rule the update check applies.
 */
export function compareAtPrecisionOf(version: string, bound: string): number {
    const parts = version.split('.')
    const limits = bound.split('.')
    for (let i = 0; i < limits.length; i++) {
        const compare = number(parts[i]) - number(limits[i])
        if (compare !== 0) return Math.sign(compare)
    }
    return 0
}

/** Whether a build runs on a Minecraft version. A build without a manifest makes no claim and does. */
export function supports(manifest: VersionManifest | null | undefined, minecraft: string | null): boolean {
    if (!manifest || !minecraft) return true
    if (manifest.minecraftMin && compareAtPrecisionOf(minecraft, manifest.minecraftMin) < 0) return false
    return !manifest.minecraftMax || compareAtPrecisionOf(minecraft, manifest.minecraftMax) <= 0
}

function compareVersions(a: string, b: string): number {
    const length = Math.max(a.split('.').length, b.split('.').length)
    const pad = (version: string) => [...version.split('.'), ...Array(length).fill('0')].slice(0, length).join('.')
    return compareAtPrecisionOf(pad(a), pad(b))
}

/** The Minecraft versions the manifests name as bounds, newest first, to filter by. */
export function minecraftVersions(versions: VersionEntry[]): string[] {
    const named = new Set<string>()
    for (const entry of versions) {
        if (entry.manifest?.minecraftMin) named.add(entry.manifest.minecraftMin)
        if (entry.manifest?.minecraftMax) named.add(entry.manifest.minecraftMax)
    }
    return [...named].sort((a, b) => compareVersions(b, a))
}

/** The site a link leads to, by name for the shortcuts and by host otherwise. */
export function linkSite(link: {type: string, url: string}): string {
    if (link.type === 'modrinth') return 'Modrinth'
    if (link.type === 'spigot') return 'SpigotMC'
    if (link.type === 'builtbybit') return 'BuiltByBit'
    if (link.type === 'hangar') return 'Hangar'
    try {
        return new URL(link.url).host
    } catch {
        return link.url
    }
}
