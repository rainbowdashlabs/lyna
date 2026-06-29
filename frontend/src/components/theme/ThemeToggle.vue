/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import { DarkMode } from '@/theme/themes'
import { useTheme } from '@/composables/useTheme'

const { darkMode, setDarkMode } = useTheme()

const icon = computed(() => {
    switch (darkMode.value) {
        case DarkMode.SYSTEM:
            return 'circle-half-stroke'
        case DarkMode.LIGHT:
            return 'sun'
        case DarkMode.DARK:
            return 'moon'
        default:
            return 'circle-half-stroke'
    }
})

function cycle() {
    switch (darkMode.value) {
        case DarkMode.SYSTEM:
            setDarkMode(DarkMode.LIGHT)
            break
        case DarkMode.LIGHT:
            setDarkMode(DarkMode.DARK)
            break
        case DarkMode.DARK:
            setDarkMode(DarkMode.SYSTEM)
            break
    }
}
</script>

<template>
    <button
        aria-label="Toggle theme"
        class="p-2 rounded-lg text-[var(--text)] transition-colors duration-150 hover:bg-bg-light-accent dark:hover:bg-bg-dark-accent"
        @click="cycle"
    >
        <font-awesome-icon :icon="['fas', icon]" class="h-5 w-5" />
    </button>
</template>
