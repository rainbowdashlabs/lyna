/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {useSession} from '~/composables/useSession'
import {signInAgain} from '~/util/signIn'

export default defineNuxtRouteMiddleware(async (to) => {
    if (typeof window === 'undefined') return
    if (!to.path.startsWith('/account') && !to.path.startsWith('/admin')) return
    const {hydrate, account} = useSession()
    await hydrate()
    if (!account.value) {
        const target = signInAgain(to.fullPath)
        return navigateTo(target.to, {external: target.external})
    }
})
