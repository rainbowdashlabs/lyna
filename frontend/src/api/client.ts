/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import axios, {type AxiosInstance, type InternalAxiosRequestConfig} from 'axios'

function readToken(): string | null {
    if (typeof localStorage === 'undefined') return null
    return localStorage.getItem('auth')
}

const client: AxiosInstance = axios.create({
    baseURL: '/',
})

client.interceptors.request.use((cfg: InternalAxiosRequestConfig) => {
    const token = readToken()
    if (token) {
        cfg.headers = cfg.headers ?? {}
        cfg.headers['Authorization'] = `Bearer ${token}`
    }
    return cfg
})

client.interceptors.response.use(
    (res) => res,
    (err) => {
        if (typeof localStorage !== 'undefined' && err?.response?.status === 401) {
            localStorage.removeItem('auth')
        }
        return Promise.reject(err)
    },
)

export default client
