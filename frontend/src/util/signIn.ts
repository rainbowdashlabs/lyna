/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * How this browser last signed in, and what to do once that session has run out.
 *
 * <p>The method is no secret and is kept in local storage. When it is Discord, a session that ran out
 * is renewed by sending the browser through Discord again, which signs a browser already signed in
 * there straight back in with no click. Every other way goes to the sign-in page.
 */
export type SignInMethod = 'discord' | 'password'

const METHOD_KEY = 'auth.method'
const ATTEMPT_KEY = 'auth.relogin'

/** How long a trip through Discord is given to come back with a session before it is not tried again. */
const ATTEMPT_WINDOW_MS = 2 * 60 * 1000

/**
 * @param next the page to come back to, a path on this site
 * @return where Discord sign-in starts
 */
export function discordStartUrl(next?: string): string {
    return next ? `/api/auth/discord/start?next=${encodeURIComponent(next)}` : '/api/auth/discord/start'
}

/** Notes how this browser signed in, or forgets it when signing out. */
export function rememberSignInMethod(method: SignInMethod | null): void {
    if (typeof localStorage === 'undefined') return
    if (method) localStorage.setItem(METHOD_KEY, method)
    else localStorage.removeItem(METHOD_KEY)
}

/** Marks the session as found again, so the next time it runs out Discord may be tried once more. */
export function settleSignIn(): void {
    if (typeof sessionStorage === 'undefined') return
    sessionStorage.removeItem(ATTEMPT_KEY)
}

/**
 * Where to send somebody whose session has run out.
 *
 * <p>Discord only once in a while: a trip that comes back without a session - cookies refused, Discord
 * declining - would otherwise go round for ever, so a second one inside the window goes to the sign-in
 * page instead.
 *
 * @param next the page they were on
 * @return the address, and whether it lies outside the application's own pages
 */
export function signInAgain(next: string): {to: string, external: boolean} {
    const login = {to: `/login?next=${encodeURIComponent(next)}`, external: false}
    if (typeof localStorage === 'undefined' || localStorage.getItem(METHOD_KEY) !== 'discord') return login
    const last = Number(sessionStorage.getItem(ATTEMPT_KEY) ?? 0)
    if (Date.now() - last < ATTEMPT_WINDOW_MS) return login
    sessionStorage.setItem(ATTEMPT_KEY, String(Date.now()))
    return {to: discordStartUrl(next), external: true}
}
