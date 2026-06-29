/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  value: number
  max?: number
  variant?: 'primary' | 'secondary' | 'success' | 'error' | 'info'
}>()
</script>

<template>
  <div class="w-full h-3 rounded-full bg-bg-light-accent dark:bg-bg-dark-accent overflow-hidden">
    <div
        :class="{
        'bg-primary': !variant || variant === 'primary',
        'bg-secondary': variant === 'secondary',
        'bg-success': variant === 'success',
        'bg-error': variant === 'error',
        'bg-info': variant === 'info',
      }"
        :style="{ width: `${Math.min(100, Math.max(0, (value / (max ?? 100)) * 100))}%` }"
        class="h-full rounded-full transition-all duration-300"
    />
  </div>
</template>
