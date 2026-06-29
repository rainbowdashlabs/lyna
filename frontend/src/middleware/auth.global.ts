import {useSession} from '~/composables/useSession'

export default defineNuxtRouteMiddleware(async (to) => {
    if (typeof window === 'undefined') return
    if (!to.path.startsWith('/account')) return
    const {hydrate, account} = useSession()
    await hydrate()
    if (!account.value) {
        return navigateTo({path: '/login', query: {next: to.fullPath}})
    }
})
