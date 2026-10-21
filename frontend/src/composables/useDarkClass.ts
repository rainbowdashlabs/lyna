/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
import {onBeforeUnmount, onMounted, ref} from 'vue'

/**
 * Whether the page is shown dark, following the `dark` class the theme puts on the root element,
 * for components that draw their own colors.
 */
export function useDarkClass() {
    const dark = ref(false)
    let observer: MutationObserver | undefined

    onMounted(() => {
        const root = document.documentElement
        const read = () => {
            dark.value = root.classList.contains('dark')
        }
        read()
        observer = new MutationObserver(read)
        observer.observe(root, {attributes: true, attributeFilter: ['class']})
    })
    onBeforeUnmount(() => observer?.disconnect())

    return dark
}
