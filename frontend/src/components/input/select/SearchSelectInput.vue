/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import MutedText from '@/components/typography/MutedText.vue'

const model = defineModel<string>()

const props = defineProps<{
  options: { value: string; label: string }[]
  placeholder?: string
  disabled?: boolean
}>()

const search = ref('')
const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)

const filteredOptions = computed(() => {
  if (!search.value) return props.options
  const q = search.value.toLowerCase()
  return props.options.filter(o => o.label.toLowerCase().includes(q) || o.value.toLowerCase().includes(q))
})

const selectedLabel = computed(() => {
  const opt = props.options.find(o => o.value === model.value)
  return opt?.label ?? model.value ?? ''
})

function select(value: string) {
  model.value = value
  open.value = false
  search.value = ''
}

function openDropdown() {
  if (props.disabled) return
  open.value = true
  search.value = ''
}

function onClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
    search.value = ''
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div ref="containerRef" class="relative">
    <button
        :disabled="disabled"
        class="w-full px-3 py-2 rounded-theme border border-bg-light-accent bg-bg-light text-(--text) text-left transition-colors duration-150 outline-none focus:border-primary focus:ring-1 focus:ring-primary disabled:opacity-50 disabled:cursor-not-allowed dark:border-bg-dark-accent dark:bg-bg-dark"
        type="button"
        @click="openDropdown"
    >
      {{ selectedLabel || placeholder || '' }}
    </button>

    <div
        v-if="open"
        class="absolute z-50 mt-1 w-full rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark max-h-60 flex flex-col"
    >
      <div class="p-2 border-b border-bg-light-accent dark:border-bg-dark-accent">
        <input
            v-model="search"
            :placeholder="placeholder ?? ''"
            class="w-full px-2 py-1.5 rounded-theme border border-bg-light-accent bg-bg-light text-(--text) text-sm outline-none focus:border-primary dark:border-bg-dark-accent dark:bg-bg-dark"
            type="text"
            @click.stop
        />
      </div>
      <div class="overflow-y-auto">
        <button
            v-for="opt in filteredOptions"
            :key="opt.value"
            :class="{ 'bg-primary/5 font-medium': opt.value === model }"
            class="w-full px-3 py-2 text-left text-sm hover:bg-primary/10 transition-colors"
            type="button"
            @click="select(opt.value)"
        >
          {{ opt.label }}
        </button>
        <MutedText tag="div" size="sm" class="py-2 px-3" v-if="filteredOptions.length === 0">
          Keine Ergebnisse
        </MutedText>
      </div>
    </div>
  </div>
</template>
