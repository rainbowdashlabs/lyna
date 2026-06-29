/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  color?: 'primary' | 'success' | 'error' | 'muted'
}>()
</script>

<template>
  <p :class="{
    'text-primary': color === 'primary' || !color,
    'text-success': color === 'success',
    'text-error': color === 'error',
    'text-(--text-muted)': color === 'muted',
  }" class="text-2xl font-bold">
    <slot/>
  </p>
</template>
