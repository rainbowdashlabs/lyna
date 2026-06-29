/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, ref} from 'vue'
import SecondaryButton from '@/components/button/SecondaryButton.vue'

const modelValue = defineModel<string[]>({required: true})

const props = defineProps<{
  options: { value: string; label: string }[]
  placeholder?: string
}>()

const open = ref(false)

const selectedSet = computed(() => new Set(modelValue.value))

const availableOptions = computed(() =>
    props.options.filter(o => !selectedSet.value.has(o.value))
)

function add(value: string) {
  modelValue.value = [...modelValue.value, value]
}

function remove(value: string) {
  modelValue.value = modelValue.value.filter(v => v !== value)
}

function getLabel(value: string): string {
  return props.options.find(o => o.value === value)?.label ?? value
}
</script>

<template>
  <div class="space-y-2">
    <!-- Selected items -->
    <div v-if="modelValue.length > 0" class="flex flex-wrap gap-1">
      <span
          v-for="val in modelValue"
          :key="val"
          class="inline-flex items-center gap-1 rounded-full px-2.5 py-1 text-xs font-medium bg-primary/10 text-primary border border-primary/20"
      >
        {{ getLabel(val) }}
        <button class="hover:text-error" type="button" @click="remove(val)">
          <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
        </button>
      </span>
    </div>

    <!-- Add dropdown -->
    <div v-if="availableOptions.length > 0" class="relative">
      <SecondaryButton :icon="['fas', 'plus']" @click="open = !open">
        {{ placeholder ?? 'Hinzufügen' }}
      </SecondaryButton>
      <div
          v-if="open"
          class="absolute z-10 mt-1 w-64 max-h-48 overflow-y-auto rounded-theme border border-bg-light-accent dark:border-bg-dark-accent bg-(--bg) shadow-lg"
      >
        <button
            v-for="opt in availableOptions"
            :key="opt.value"
            class="w-full px-3 py-2 text-sm text-left hover:bg-primary/5 transition-colors"
            type="button"
            @click="add(opt.value); open = false"
        >
          {{ opt.label }}
        </button>
      </div>
    </div>
  </div>
</template>
