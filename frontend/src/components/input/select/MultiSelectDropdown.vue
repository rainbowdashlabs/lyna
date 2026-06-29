/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

export interface SelectOption {
  value: string
  label: string
  group?: string
}

const modelValue = defineModel<string[]>({required: true})

const props = defineProps<{
  options: SelectOption[]
  placeholder?: string
  disabled?: boolean
  searchable?: boolean
}>()

const open = ref(false)
const containerRef = ref<HTMLElement | null>(null)
const searchQuery = ref('')

const selectedSet = computed(() => new Set(modelValue.value))

const filteredOptions = computed(() => {
  if (!props.searchable || !searchQuery.value.trim()) return props.options
  const q = searchQuery.value.toLowerCase()
  return props.options.filter(o => o.label.toLowerCase().includes(q))
})

const triggerLabel = computed(() => {
  if (modelValue.value.length === 0) return props.placeholder ?? 'Auswahl'
  const selectedLabels = props.options
      .filter(o => selectedSet.value.has(o.value))
      .map(o => o.label)
  if (selectedLabels.length <= 2) return selectedLabels.join(', ')
  return `${selectedLabels.slice(0, 2).join(', ')} +${selectedLabels.length - 2}`
})

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

const allSelected = computed(() => props.options.length > 0 && modelValue.value.length === props.options.length)

function toggle(value: string) {
  if (selectedSet.value.has(value)) {
    modelValue.value = modelValue.value.filter(v => v !== value)
  } else {
    modelValue.value = [...modelValue.value, value]
  }
}

function selectAll() {
  modelValue.value = props.options.map(o => o.value)
}

function selectNone() {
  modelValue.value = []
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
      {{ triggerLabel }}
      <font-awesome-icon
        :icon="['fas', 'chevron-down']"
        :class="['ml-1.5 h-3 w-3 transition-transform duration-150', open ? 'rotate-180' : '']"
      />
    </SecondaryButton>

    <div
      v-if="open"
      class="absolute z-20 mt-1 w-64 max-h-72 rounded-theme border border-bg-light-accent bg-bg-light shadow-lg dark:border-bg-dark-accent dark:bg-bg-dark flex flex-col"
    >
      <!-- Select all / none -->
      <div class="flex gap-2 px-3 py-2 border-b border-bg-light-accent dark:border-bg-dark-accent text-xs">
        <button
          type="button"
          class="text-primary hover:underline cursor-pointer"
          :class="{'opacity-50': allSelected}"
          :disabled="allSelected"
          @click.stop="selectAll"
        >
          Alle auswählen
        </button>
        <span class="text-[var(--text)]">/</span>
        <button
          type="button"
          class="text-primary hover:underline cursor-pointer"
          :class="{'opacity-50': modelValue.length === 0}"
          :disabled="modelValue.length === 0"
          @click.stop="selectNone"
        >
          Keine
        </button>
      </div>

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
            class="w-full flex items-center gap-2 px-3 py-2 text-sm text-left hover:bg-primary/5 transition-colors cursor-pointer"
            @click.stop="toggle(opt.value)"
          >
            <font-awesome-icon
              :icon="['fas', selectedSet.has(opt.value) ? 'square-check' : 'square']"
              :class="selectedSet.has(opt.value) ? 'text-primary' : 'text-[var(--text)] opacity-40'"
              class="h-4 w-4"
            />
            <span>{{ opt.label }}</span>
          </button>
        </template>
      </div>
    </div>
  </div>
</template>
