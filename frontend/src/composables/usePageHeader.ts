/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {readonly, ref} from 'vue'

/**
 * Reactive header state shared across the app. {@code ViewContent} writes to it
 * from its {@code title} / {@code subtitle} props; the outer layout components
 * ({@code AdminView}, {@code StationView}, {@code HelpcenterView}) read from it
 * and forward the values to their sidebar/header chrome. The browser tab title
 * uses the same source via {@code usePageTitle}.
 */
const title = ref('')
const subtitle = ref('')

let owner: symbol | null = null

export function usePageHeader() {
    return {title: readonly(title), subtitle: readonly(subtitle)}
}

/**
 * Binds the shared page header to a single owning component instance. Writing claims
 * ownership; releasing clears the header only while the caller still owns it. During a
 * route change the incoming page can write its header before the outgoing page unmounts -
 * without the ownership check, the outgoing page's cleanup would wipe the values the new
 * page just set, leaving every page after the first navigation without title and subtitle.
 */
export function claimPageHeader() {
    const id = Symbol('page-header-owner')
    return {
        set(newTitle: string, newSubtitle: string) {
            owner = id
            title.value = newTitle
            subtitle.value = newSubtitle
        },
        release() {
            if (owner !== id) return
            owner = null
            title.value = ''
            subtitle.value = ''
        },
    }
}
