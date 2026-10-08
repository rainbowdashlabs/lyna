/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
/**
 * Data a public page needs while it is rendered on the server, for what a crawler reads: titles,
 * previews. On the server the backend is asked directly; in the browser the same path goes through
 * this site's proxy. A failure answers null rather than failing the page.
 */
export function useServerFetch<T>(key: string, path: string) {
    const base = import.meta.server ? useRuntimeConfig().backendUrl : ''
    return useAsyncData<T | null>(key, () => $fetch<T>(`${base}${path}`).catch(() => null))
}
