/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  variant: 'info' | 'success' | 'error'
}>()
</script>

<template>
  <div
      :class="{
      'border-info bg-info/10': variant === 'info',
      'border-success bg-success/10': variant === 'success',
      'border-error bg-error/10': variant === 'error',
    }"
      class="flex items-start gap-3 rounded-theme border p-4"
  >
    <font-awesome-icon
        :class="{
        'text-info-badge': variant === 'info',
        'text-success-badge': variant === 'success',
        'text-error-badge': variant === 'error',
      }"
        :icon="['fas', variant === 'success' ? 'circle-check' : variant === 'error' ? 'triangle-exclamation' : 'circle-info']"
        class="mt-0.5 shrink-0"
    />
    <div class="text-sm">
      <slot/>
    </div>
  </div>
</template>
