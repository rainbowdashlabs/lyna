/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  selected: boolean
  size?: 'sm' | 'md'
}>()

defineEmits<{
  toggle: []
}>()
</script>

<template>
  <button
    type="button"
    :class="[
      'rounded-theme font-medium border transition-all cursor-pointer',
      size === 'md' ? 'px-3 py-1.5 text-sm' : 'px-3 py-1.5 text-xs',
      selected
        ? 'border-primary bg-primary/10 text-primary ring-1 ring-primary/30'
        : 'border-bg-light-accent dark:border-bg-dark-accent text-(--text-muted) hover:border-primary',
    ]"
    @click="$emit('toggle')"
  >
    <slot />
  </button>
</template>
