/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {computed, readonly, ref} from 'vue'
import {getAdminAccess, type AdminGuild} from '~/api/admin'

const guilds = ref<AdminGuild[]>([])
const operator = ref(false)
const loaded = ref(false)
let loading: Promise<void> | null = null

async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    if (loading) return loading
    loading = (async () => {
        try {
            const access = await getAdminAccess()
            guilds.value = access.guilds
            operator.value = access.operator
        } finally {
            loaded.value = true
            loading = null
        }
    })()
    return loading
}

/**
 * Whether there is anything to administer at all: a guild, or the instance. An operator of an
 * instance with no guild to list still has the instance area.
 */
const canAdminister = computed(() => operator.value || guilds.value.length > 0)

export function useAdminGuilds() {
    return {
        guilds: readonly(guilds),
        operator: readonly(operator),
        canAdminister,
        loaded: readonly(loaded),
        load,
    }
}
