/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed, onBeforeUnmount, onMounted, ref } from 'vue'

export interface IconOption {
    value: string
    label: string
    icon: string[]
    color?: string
}

const model = defineModel<string>()

const props = defineProps<{
    options: IconOption[]
    disabled?: boolean
    autoOpen?: boolean
}>()

const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const selected = computed(() => props.options.find(o => o.value === model.value))

function select(value: string) {
    model.value = value
    open.value = false
}

function handleClickOutside(e: MouseEvent) {
    if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
        open.value = false
    }
}

onMounted(() => { document.addEventListener('click', handleClickOutside); if (props.autoOpen) open.value = true })
onBeforeUnmount(() => document.removeEventListener('click', handleClickOutside))
</script>

<template>
    <div ref="containerRef" class="relative">
        <button
            type="button"
            :disabled="disabled"
            class="w-full flex items-center gap-2 rounded-theme border border-[var(--border)] bg-[var(--bg)] px-3 py-2 text-sm text-left transition-colors hover:border-primary disabled:opacity-50"
            @click="open = !open"
        >
            <font-awesome-icon v-if="selected" :icon="selected.icon" :class="selected.color" />
            <span class="flex-1">{{ selected?.label ?? '' }}</span>
            <font-awesome-icon :icon="['fas', 'chevron-down']" class="text-xs text-(--text-muted)" />
        </button>
        <div v-if="open" class="absolute z-20 mt-1 w-full rounded-theme border border-[var(--border)] bg-[var(--bg)] shadow-lg overflow-hidden">
            <button
                v-for="opt in options"
                :key="opt.value"
                type="button"
                :class="opt.value === model ? 'bg-primary/10' : 'hover:bg-primary/5'"
                class="w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors"
                @click="select(opt.value)"
            >
                <font-awesome-icon :icon="opt.icon" :class="opt.color" />
                <span>{{ opt.label }}</span>
            </button>
        </div>
    </div>
</template>
