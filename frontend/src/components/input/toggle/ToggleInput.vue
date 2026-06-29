/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<boolean>({default: false})

defineProps<{
  disabled?: boolean
}>()
</script>

<template>
  <button
      :aria-checked="model"
      :class="model ? 'bg-success' : 'bg-error'"
      :disabled="disabled"
      class="relative inline-flex h-6 w-12 shrink-0 cursor-pointer rounded-full border-2 border-transparent transition-colors duration-200 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-2 disabled:opacity-50 disabled:cursor-not-allowed"
      role="switch"
      type="button"
      @click="model = !model"
  >
    <!-- Inactive icon (cross) -->
    <span
        :class="model ? 'opacity-0' : 'opacity-100'"
        class="absolute inset-0 flex items-center justify-end pr-0.5 pointer-events-none transition-opacity duration-200 text-error-text"
    >
      <font-awesome-icon :icon="['fas', 'xmark']" class="h-3 w-3"/>
    </span>
    <!-- Active icon (check) -->
    <span
        :class="model ? 'opacity-100' : 'opacity-0'"
        class="absolute inset-0 flex items-center justify-start pl-0.5 pointer-events-none transition-opacity duration-200 text-success-text"
    >
      <font-awesome-icon :icon="['fas', 'check']" class="h-3 w-3"/>
    </span>
    <!-- Knob -->
    <span
        :class="model ? 'translate-x-6' : 'translate-x-0'"
        class="pointer-events-none inline-block h-5 w-5 rounded-full bg-white shadow ring-0 transition-transform duration-200"
    />
  </button>
</template>
