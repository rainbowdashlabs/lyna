import {readonly, ref} from 'vue'
import {listAdminGuilds, type AdminGuild} from '~/api/admin'

const guilds = ref<AdminGuild[]>([])
const loaded = ref(false)
let loading: Promise<void> | null = null

async function load(force = false): Promise<void> {
    if (loaded.value && !force) return
    if (loading) return loading
    loading = (async () => {
        try {
            guilds.value = await listAdminGuilds()
        } finally {
            loaded.value = true
            loading = null
        }
    })()
    return loading
}

export function useAdminGuilds() {
    return {
        guilds: readonly(guilds),
        loaded: readonly(loaded),
        load,
    }
}
