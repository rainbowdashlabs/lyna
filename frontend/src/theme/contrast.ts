/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
const WHITE = '#ffffff'
const DARK = '#1a1a1a'

function linearize(c: number): number {
    return c <= 0.04045 ? c / 12.92 : Math.pow((c + 0.055) / 1.055, 2.4)
}

function relativeLuminance(hex: string): number {
    const r = parseInt(hex.slice(1, 3), 16) / 255
    const g = parseInt(hex.slice(3, 5), 16) / 255
    const b = parseInt(hex.slice(5, 7), 16) / 255
    return 0.2126 * linearize(r) + 0.7152 * linearize(g) + 0.0722 * linearize(b)
}

export function contrastRatio(hex1: string, hex2: string): number {
    const l1 = relativeLuminance(hex1)
    const l2 = relativeLuminance(hex2)
    const lighter = Math.max(l1, l2)
    const darker = Math.min(l1, l2)
    return (lighter + 0.05) / (darker + 0.05)
}

export function contrastTextColor(bgHex: string): string {
    return contrastRatio(bgHex, WHITE) >= contrastRatio(bgHex, DARK) ? WHITE : DARK
}

function hexToRgb(hex: string): [number, number, number] {
    return [
        parseInt(hex.slice(1, 3), 16),
        parseInt(hex.slice(3, 5), 16),
        parseInt(hex.slice(5, 7), 16),
    ]
}

function rgbToHex(r: number, g: number, b: number): string {
    return '#' + [r, g, b].map(c => Math.round(Math.max(0, Math.min(255, c))).toString(16).padStart(2, '0')).join('')
}

/**
 * Darkens or lightens a colour until it reads against the background it sits on.
 *
 * <p>A theme's accent is chosen to look right as a fill, and the same value as text on the page
 * background is often unreadable. Badges and labels go through here so they stay legible in every
 * theme without each theme having to name a second colour for them.
 */
export function ensureContrast(fgHex: string, bgHex: string, minRatio: number = 4.5): string {
    if (contrastRatio(fgHex, bgHex) >= minRatio) return fgHex

    const bgLum = relativeLuminance(bgHex)
    const [r, g, b] = hexToRgb(fgHex)
    const shouldDarken = bgLum > 0.5

    for (let step = 1; step <= 30; step++) {
        const factor = shouldDarken ? 1 - step * 0.03 : 1 + step * 0.05
        const adjusted = rgbToHex(
            shouldDarken ? r * factor : r + (255 - r) * (factor - 1),
            shouldDarken ? g * factor : g + (255 - g) * (factor - 1),
            shouldDarken ? b * factor : b + (255 - b) * (factor - 1),
        )
        if (contrastRatio(adjusted, bgHex) >= minRatio) return adjusted
    }
    return shouldDarken ? DARK : WHITE
}
