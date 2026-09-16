/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  /** The fill, for the two badges that carry a state. The rest are outlines. */
  bgClass?: string
}>()
</script>

<template>
  <span
      :class="bgClass ?? 'border border-(--border) text-(--text-muted)'"
      class="font-data rounded-theme inline-flex items-center px-2 py-0.5 text-[10px] tracking-[0.1em] uppercase"
  >
    <slot/>
  </span>
</template>
