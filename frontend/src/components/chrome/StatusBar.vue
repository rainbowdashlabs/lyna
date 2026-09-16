/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
export interface StatusItem {
    label: string
    /** Draws the item as a state rather than a fact: green for good, pink for waiting. */
    tone?: 'ok' | 'pending'
}

defineProps<{
    left: StatusItem[]
    right?: StatusItem[]
}>()
</script>

<template>
  <footer class="font-data flex flex-wrap items-center gap-x-5 gap-y-1 border-t border-border-light bg-bg-light-accent px-4 py-1.5 text-[11px] text-(--text-muted) dark:border-border-dark dark:bg-bg-dark-accent">
    <span v-for="item in left" :key="item.label" class="flex items-center gap-1.5">
      <i v-if="item.tone" :class="item.tone === 'ok' ? 'bg-success' : 'bg-primary'" class="block size-1.5"/>
      {{ item.label }}
    </span>
    <span class="ml-auto flex flex-wrap items-center gap-x-5">
      <span v-for="item in right ?? []" :key="item.label">{{ item.label }}</span>
    </span>
  </footer>
</template>
