/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script setup lang="ts">
defineProps<{ for?: string }>()
</script>

<template>
  <label :for="$props.for" class="text-sm text-[var(--text-muted)] mb-1 block">
    <slot />
  </label>
</template>
