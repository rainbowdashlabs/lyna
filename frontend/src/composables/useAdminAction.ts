/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'

/**
 * One admin action at a time: whether it is running, whether it just succeeded, and what went wrong.
 *
 * <p>The admin endpoints answer a refusal as plain text written for the reader ("Still offered by
 * …"), so that text is shown as it is; only when there is none does the fallback stand in.
 */
export function useAdminAction(fallback: () => string) {
    const busy = ref(false)
    const done = ref(false)
    const error = ref<string | null>(null)

    async function run<T>(action: () => Promise<T>): Promise<T | undefined> {
        busy.value = true
        done.value = false
        error.value = null
        try {
            const result = await action()
            done.value = true
            return result
        } catch (e) {
            const data = (e as { response?: { data?: unknown } }).response?.data
            error.value = typeof data === 'string' && data ? data : fallback()
            return undefined
        } finally {
            busy.value = false
        }
    }

    return {busy, done, error, run}
}
