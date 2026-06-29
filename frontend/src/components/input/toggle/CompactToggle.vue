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
      :class="[
        model ? 'bg-success border-success' : 'bg-transparent border-[var(--border)]',
        disabled ? 'opacity-50 cursor-not-allowed' : 'cursor-pointer hover:border-primary'
      ]"
      :disabled="disabled"
      class="inline-flex items-center justify-center h-5 w-5 shrink-0 rounded border-2 transition-colors duration-150 focus:outline-none focus:ring-2 focus:ring-primary focus:ring-offset-1"
      role="switch"
      type="button"
      @click="model = !model"
  >
    <font-awesome-icon v-if="model" :icon="['fas', 'check']" class="h-2.5 w-2.5 text-success-text"/>
  </button>
</template>
