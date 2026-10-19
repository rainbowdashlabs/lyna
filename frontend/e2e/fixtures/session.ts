/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {type APIRequestContext, expect, test as base} from '@playwright/test'
import {type Account, PASSWORD, SESSION_COOKIE} from './auth'
import {uniqueEmail} from './unique'

/** What the web client sends with every request, and the backend asks of any change made with a session. */
export const CLIENT_HEADER = {'X-Lyna-Client': 'web'}

export interface SignedUp {
    email: string
    api: APIRequestContext
}

interface SessionFixtures {
    /** A request context with no session, as a fresh browser has. */
    anonymous: () => Promise<APIRequestContext>
    /** A request context signed in as an existing account, holding that account's session cookie. */
    signedIn: (account: Account) => Promise<APIRequestContext>
    /** Signs a new account up under an address of its own, and the context holding its session. */
    signedUp: (prefix: string) => Promise<SignedUp>
}

/**
 * The suite's `test`, with an API session per account.
 *
 * <p>A session is a cookie now, and a request context keeps the cookies it is given. One context per
 * account is what keeps two accounts in one story apart; sharing the story's own `request` would have
 * the second sign-in replace the first. Every context speaks as the web client does, and is disposed
 * of when the story ends.
 */
export const test = base.extend<SessionFixtures & {newApi: () => Promise<APIRequestContext>}>({
    newApi: async ({playwright, baseURL}, use) => {
        const made: APIRequestContext[] = []
        await use(async () => {
            const context = await playwright.request.newContext({baseURL, extraHTTPHeaders: CLIENT_HEADER})
            made.push(context)
            return context
        })
        await Promise.all(made.map(context => context.dispose()))
    },
    anonymous: async ({newApi}, use) => {
        await use(newApi)
    },
    signedIn: async ({newApi}, use) => {
        await use(async account => {
            const api = await newApi()
            const login = await api.post('/api/auth/login', {data: account})
            expect(login.ok(), `signing in as ${account.email}`).toBe(true)
            return api
        })
    },
    signedUp: async ({newApi}, use) => {
        await use(async prefix => {
            const api = await newApi()
            const email = uniqueEmail(prefix)
            const signup = await api.post('/api/auth/signup', {data: {email, password: PASSWORD}})
            expect(signup.ok(), `signing up ${email}`).toBe(true)
            return {email, api}
        })
    },
})

export {expect}

/**
 * @return the session cookie a request context holds, or undefined when it holds none
 */
export async function sessionCookieOf(api: APIRequestContext): Promise<string | undefined> {
    const state = await api.storageState()
    return state.cookies.find(cookie => cookie.name === SESSION_COOKIE)?.value
}
