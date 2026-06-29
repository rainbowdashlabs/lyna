/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import { computed } from 'vue'
import IconButton from '@/components/button/IconButton.vue'

/**
 * An IconButton that renders in the muted text color and shifts to a hover accent.
 * Used for low-emphasis row actions throughout the app.
 */
const props = defineProps<{
  icon: string | string[]
  label: string
  disabled?: boolean
  hover?: 'primary' | 'error' | 'text'
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()

const hoverClass = computed(() => {
  switch (props.hover ?? 'primary') {
    case 'error':
      return 'hover:text-error'
    case 'text':
      return 'hover:text-(--text)'
    case 'primary':
    default:
      return 'hover:text-primary'
  }
})
</script>

<template>
  <IconButton
      :icon="icon"
      :label="label"
      :disabled="disabled"
      :class="['text-(--text-muted)', hoverClass]"
      @click="$emit('click', $event)"
  />
</template>
