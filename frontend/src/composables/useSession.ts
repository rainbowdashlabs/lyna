/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'
import client, {handleUnauthorized} from '~/api/client'
import {settleSignIn, signInAgain} from '~/util/signIn'

interface SessionAccount {
    id: number
    email: string | null
    hasPassword: boolean
    discordId: string | null
    username: string | null
    theme: string | null
    darkMode: string | null
}

/** Where sessions from before the cookie kept their token. Read once to move it over, then gone. */
const LEGACY_TOKEN_KEY = 'auth'

const account = ref<SessionAccount | null>(null)
const ready = ref(false)
let hydrating: Promise<void> | null = null

/**
 * Hands a token kept in local storage by an older version to the backend, which answers with the
 * session cookie for the same session.
 *
 * <p>The token is forgotten once the backend has answered, whatever it said. Not before: a page left
 * while the request is under way would otherwise lose the token without the cookie ever arriving,
 * and sign somebody out who was meant to stay in.
 */
async function adoptLegacyToken(): Promise<void> {
    if (typeof localStorage === 'undefined') return
    const legacy = localStorage.getItem(LEGACY_TOKEN_KEY)
    if (!legacy) return
    const answered = await client.post('/api/auth/adopt', null, {headers: {Authorization: `Bearer ${legacy}`}})
        .then(() => true)
        .catch((error: {response?: unknown}) => error.response !== undefined)
    if (answered) localStorage.removeItem(LEGACY_TOKEN_KEY)
}

/**
 * Asks the backend who this browser is signed in as. The account is the session: there is nothing
 * else on this side to hold.
 */
async function hydrate(): Promise<void> {
    if (hydrating) return hydrating
    hydrating = (async () => {
        try {
            await adoptLegacyToken()
            const {data} = await client.get<SessionAccount>('/api/auth/me')
            account.value = data
            settleSignIn()
        } catch {
            account.value = null
        } finally {
            ready.value = true
        }
    })()
    return hydrating
}

/** Asks again, for after the backend has just set or cleared the cookie. */
function refresh(): Promise<void> {
    hydrating = null
    ready.value = false
    return hydrate()
}

/**
 * A 401 while somebody was signed in may mean the session ran out underneath the page - or only that
 * one call was refused. Asking who is signed in tells the two apart; only a session that is really
 * gone sends anyone to sign in again, and only from the pages that need one.
 */
handleUnauthorized(error => {
    if (!account.value || error.config?.url === '/api/auth/me') return
    void refresh().then(() => {
        if (account.value || typeof window === 'undefined') return
        const next = window.location.pathname + window.location.search
        if (!next.startsWith('/account') && !next.startsWith('/admin')) return
        const target = signInAgain(next)
        if (target.external) window.location.assign(target.to)
        else void navigateTo(target.to)
    })
})

export function useSession() {
    return {
        account: readonly(account),
        ready: readonly(ready),
        hydrate,
        refresh,
        clear() {
            account.value = null
            hydrating = null
            ready.value = true
        },
    }
}

export type {SessionAccount}
