/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  hint?: boolean
  inline?: boolean
}>()
</script>

<template>
  <label :class="[
    hint ? 'text-xs text-(--text-muted)' : 'text-sm',
    inline ? 'flex items-center gap-2' : 'block font-medium',
  ]">
    <slot/>
  </label>
</template>
