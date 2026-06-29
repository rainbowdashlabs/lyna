/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  disabled?: boolean
  fullWidth?: boolean
  icon?: string[] | null
  compact?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      type="button"
      :disabled="disabled"
      :class="[
        fullWidth ? 'w-full justify-center' : '',
        compact ? 'px-2 py-1 text-xs' : 'px-3 py-1.5 text-sm',
      ]"
      class="inline-flex items-center rounded-theme font-medium transition-all duration-150 active:scale-95 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100"
      @click="$emit('click', $event)"
  >
    <font-awesome-icon v-if="icon" :icon="icon" class="mr-1"/>
    <slot/>
  </button>
</template>
