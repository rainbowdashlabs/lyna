/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  icon: string | string[]
  disabled?: boolean
  label: string
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      :aria-label="label"
      :disabled="disabled"
      :title="label"
      class="inline-flex items-center justify-center p-2 rounded-theme transition-all duration-150 active:scale-90 disabled:opacity-50 disabled:cursor-not-allowed disabled:active:scale-100"
      @click="$emit('click', $event)"
  >
    <slot>
      <font-awesome-icon :icon="icon" class="h-4 w-4"/>
    </slot>
  </button>
</template>
