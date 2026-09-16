/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {THEMES} from '~/theme/themes'

const selected = defineModel<string>({required: true})

const props = defineProps<{
    /** The themes the operator allows. Empty means every theme in the catalog. */
    enabled: string[]
    disabled?: boolean
}>()

const choices = computed(() => Object.entries(THEMES)
    .filter(([key]) => props.enabled.length === 0 || props.enabled.includes(key))
    .map(([key, theme]) => ({key, label: theme.label, colors: theme.colors})))
</script>

<template>
  <div class="grid grid-cols-2 gap-3 sm:grid-cols-3">
    <button
        v-for="choice in choices"
        :key="choice.key"
        :class="selected === choice.key ? 'border-primary' : 'border-border-light dark:border-border-dark'"
        :disabled="disabled"
        class="rounded-theme border p-3 text-left transition-colors hover:border-primary disabled:cursor-not-allowed disabled:opacity-50"
        @click="selected = choice.key"
    >
      <div class="mb-2 flex gap-1">
        <span
            v-for="swatch in [choice.colors.light.primary, choice.colors.light.secondary, choice.colors.light.info]"
            :key="swatch"
            :style="{backgroundColor: swatch}"
            class="h-5 w-5 rounded-theme"
        />
      </div>
      <div class="text-sm font-medium">{{ choice.label }}</div>
    </button>
  </div>
</template>
