/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'
import type {SelectOption} from '@/components/input/select/MultiSelectDropdown.vue'

const modelValue = defineModel<string>({required: true})

const props = defineProps<{
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  clearable?: boolean
  searchable?: boolean
}>()

const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const searchQuery = ref('')

const filteredOptions = computed(() => {
  if (!props.searchable || !searchQuery.value.trim()) return props.options
  const q = searchQuery.value.toLowerCase()
  return props.options.filter(o => o.label.toLowerCase().includes(q))
})

const selectedLabel = computed(() => {
  const opt = props.options.find(o => o.value === modelValue.value)
  return opt?.label ?? props.placeholder ?? 'Auswahl'
})

const hasSelection = computed(() => modelValue.value !== '' && props.options.some(o => o.value === modelValue.value))

const groupedOptions = computed(() => {
  const opts = filteredOptions.value
  const hasGroups = opts.some(o => o.group)
  if (!hasGroups) return [{group: undefined as string | undefined, options: opts}]

  const map = new Map<string | undefined, SelectOption[]>()
  for (const opt of opts) {
    const key = opt.group
    if (!map.has(key)) map.set(key, [])
    map.get(key)!.push(opt)
  }
  return Array.from(map.entries()).map(([group, options]) => ({group, options}))
})

function select(value: string) {
  modelValue.value = value
  open.value = false
}

function clear() {
  modelValue.value = ''
  open.value = false
}

function onClickOutside(e: MouseEvent) {
  if (containerRef.value && !containerRef.value.contains(e.target as Node)) {
    open.value = false
    searchQuery.value = ''
  }
}

onMounted(() => document.addEventListener('click', onClickOutside))
onBeforeUnmount(() => document.removeEventListener('click', onClickOutside))
</script>

<template>
  <div ref="containerRef" class="relative inline-block">
    <SecondaryButton :disabled="disabled" @click="open = !open">
      {{ selectedLabel }}
      <font-awesome-icon
        :icon="['fas', 'chevron-down']"
        :class="['ml-1.5 h-3 w-3 transition-transform duration-150', open ? 'rotate-180' : '']"
      />
    </SecondaryButton>

    <div
      v-if="open"
      class="absolute z-20 mt-1 w-64 max-h-72 rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark flex flex-col"
    >
      <!-- Clear option -->
      <button
        v-if="clearable && hasSelection"
        type="button"
        class="w-full flex items-center gap-2 px-3 py-2 text-sm text-left text-error hover:bg-error/5 border-b border-bg-light-accent dark:border-bg-dark-accent transition-colors cursor-pointer"
        @click.stop="clear"
      >
        <font-awesome-icon :icon="['fas', 'xmark']" class="h-4 w-4" />
        <span>Auswahl aufheben</span>
      </button>

      <!-- Search -->
      <div v-if="searchable" class="px-2 py-1.5 border-b border-bg-light-accent dark:border-bg-dark-accent">
        <input
            v-model="searchQuery"
            type="text"
            class="w-full px-2 py-1 text-sm rounded border border-bg-light-accent dark:border-bg-dark-accent bg-transparent focus:outline-none focus:border-primary"
            placeholder="Suche…"
            @click.stop
        />
      </div>

      <!-- Options -->
      <div class="overflow-y-auto">
        <template v-for="(section, idx) in groupedOptions" :key="idx">
          <div
            v-if="section.group"
            class="px-3 py-1.5 text-xs font-semibold text-[var(--text)] opacity-60 uppercase tracking-wide"
          >
            {{ section.group }}
          </div>
          <button
            v-for="opt in section.options"
            :key="opt.value"
            type="button"
            :class="[
              'w-full flex items-center gap-2 px-3 py-2 text-sm text-left transition-colors cursor-pointer',
              opt.value === modelValue
                ? 'bg-primary/10 text-primary font-medium'
                : 'hover:bg-primary/5'
            ]"
            @click.stop="select(opt.value)"
          >
            <font-awesome-icon
              v-if="opt.value === modelValue"
              :icon="['fas', 'check']"
              class="h-3 w-3 text-primary"
            />
            <span :class="opt.value !== modelValue ? 'ml-5' : ''">{{ opt.label }}</span>
          </button>
        </template>
      </div>
    </div>
  </div>
</template>
