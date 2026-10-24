/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * The licence key a claim link carried, kept in this tab while its holder signs up or signs in.
 *
 * <p>Session storage rather than the address they return to: Discord sign-in passes that address
 * through the backend, and a key has no business in a server's logs.
 */
const PENDING_KEY = 'claim.key'

/** Keeps the key for the way back from signing in. */
export function rememberClaim(key: string): void {
    if (typeof sessionStorage === 'undefined') return
    sessionStorage.setItem(PENDING_KEY, key)
}

/** @return the key waiting to be claimed in this tab, if any */
export function pendingClaim(): string | null {
    if (typeof sessionStorage === 'undefined') return null
    return sessionStorage.getItem(PENDING_KEY)
}

/** Drops the key once it has been claimed. */
export function forgetClaim(): void {
    if (typeof sessionStorage === 'undefined') return
    sessionStorage.removeItem(PENDING_KEY)
}

/** @return the key a claim link carries after its {@code #}, or null when it carries none */
export function keyFromHash(hash: string): string | null {
    const key = decodeURIComponent(hash.replace(/^#/, '')).trim()
    return key ? key : null
}
