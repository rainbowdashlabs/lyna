import {readonly, ref} from 'vue'
import client from '~/api/client'

interface SessionAccount {
    id: number
    email: string | null
    hasPassword: boolean
    discordId: string | null
    username: string | null
    theme: string | null
    darkMode: string | null
}

const STORAGE_KEY = 'auth'

const token = ref<string | null>(null)
const account = ref<SessionAccount | null>(null)
const ready = ref(false)
let hydrating: Promise<void> | null = null

function readStoredToken(): string | null {
    if (typeof localStorage === 'undefined') return null
    return localStorage.getItem(STORAGE_KEY)
}

function writeToken(next: string | null) {
    token.value = next
    if (typeof localStorage === 'undefined') return
    if (next) localStorage.setItem(STORAGE_KEY, next)
    else localStorage.removeItem(STORAGE_KEY)
}

async function hydrate(): Promise<void> {
    if (hydrating) return hydrating
    hydrating = (async () => {
        const stored = readStoredToken()
        token.value = stored
        if (!stored) {
            account.value = null
            ready.value = true
            return
        }
        try {
            const {data} = await client.get<SessionAccount>('/api/auth/me')
            account.value = data
        } catch {
            writeToken(null)
            account.value = null
        } finally {
            ready.value = true
        }
    })()
    return hydrating
}

export function useSession() {
    return {
        token: readonly(token),
        account: readonly(account),
        ready: readonly(ready),
        hydrate,
        setToken(next: string) {
            writeToken(next)
            hydrating = null
            ready.value = false
        },
        clear() {
            writeToken(null)
            account.value = null
            hydrating = null
            ready.value = true
        },
    }
}

export type {SessionAccount}
