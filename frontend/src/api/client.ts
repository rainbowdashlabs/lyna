/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import axios, {type AxiosError, type AxiosInstance} from 'axios'

/**
 * The client every call to the backend goes through.
 *
 * <p>The session travels in a cookie the page cannot read, so nothing here holds a token. Every request
 * names this client in `X-Lyna-Client`, which the backend asks of anything that changes something with
 * the session cookie: a page on another site can make the browser send the cookie, but not that header.
 */
const client: AxiosInstance = axios.create({
    baseURL: '/',
    headers: {'X-Lyna-Client': 'web'},
})

type UnauthorizedHandler = (error: AxiosError) => void

let onUnauthorized: UnauthorizedHandler | null = null

/**
 * Names what happens when the backend answers 401, which is the session's business rather than the
 * client's.
 */
export function handleUnauthorized(handler: UnauthorizedHandler): void {
    onUnauthorized = handler
}

client.interceptors.response.use(
    (res) => res,
    (err: AxiosError) => {
        if (err?.response?.status === 401) onUnauthorized?.(err)
        return Promise.reject(err)
    },
)

export default client
