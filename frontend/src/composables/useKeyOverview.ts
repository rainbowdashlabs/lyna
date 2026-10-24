/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {
    type ProductAllowance,
    refusalOf,
    type UpdateToken,
    type UpdateTokenOverview,
    type UpdateTokenRequest,
    updateTokenOverview,
} from '~/api/updateTokens'

/**
 * The account's server keys, loaded once and kept true after every change: the upgrade tokens on the
 * Server keys page and the keys of a product's key types on that product's page read the same overview.
 */
export function useKeyOverview() {
    const {t} = useI18n()
    const data = ref<UpdateTokenOverview | null>(null)
    const loading = ref(true)
    const busy = ref(false)
    const errorMessage = ref<string | null>(null)

    async function load() {
        try {
            data.value = await updateTokenOverview()
        } catch (e) {
            errorMessage.value = (e as Error).message
        } finally {
            loading.value = false
        }
    }

    /** Runs a change, says why if it was refused, and reloads either way so the counts stay true. */
    async function act<T>(change: () => Promise<T>): Promise<T | undefined> {
        busy.value = true
        errorMessage.value = null
        try {
            return await change()
        } catch (e) {
            const refusal = refusalOf(e)
            errorMessage.value = refusal ? t(`page.account.updaters.refusal.${refusal}`) : (e as Error).message
            return undefined
        } finally {
            busy.value = false
            await load()
        }
    }

    /** The keys counted against an allowance: of its product, and of its key type or none. */
    function keysOf(allowance: ProductAllowance): UpdateToken[] {
        return data.value?.tokens.filter(token =>
            token.productId === allowance.productId && token.keyTypeId === allowance.keyTypeId) ?? []
    }

    /** The newest request for more of an allowance, if there was one. */
    function lastRequestOf(allowance: ProductAllowance): UpdateTokenRequest | null {
        return data.value?.requests.find(request =>
            request.productId === allowance.productId && request.keyTypeId === allowance.keyTypeId) ?? null
    }

    return {data, loading, busy, errorMessage, load, act, keysOf, lastRequestOf}
}
