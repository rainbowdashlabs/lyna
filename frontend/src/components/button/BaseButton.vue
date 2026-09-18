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
      class="rounded-theme inline-flex cursor-pointer items-center border font-medium transition-colors duration-100 disabled:cursor-not-allowed disabled:opacity-50"
      @click="$emit('click', $event)"
  >
    <font-awesome-icon v-if="icon" :icon="icon" class="mr-1"/>
    <slot/>
  </button>
</template>
