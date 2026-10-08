/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * A tab that switches what a page shows rather than where it is: {@code TabStrip}'s look, without a
 * route behind it. The active one carries the same pink edge.
 */
defineProps<{
    active: boolean
    /** A count shown after the label, such as how many matches a search found there. */
    count?: number | null
}>()

defineEmits<{
    select: []
}>()
</script>

<template>
  <button
      :aria-selected="active"
      :class="active ? 'tab-on' : ''"
      class="font-data flex shrink-0 cursor-pointer items-center gap-2 border-r border-border-light px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:bg-primary/10 hover:text-(--text) dark:border-border-dark"
      role="tab"
      type="button"
      @click="$emit('select')"
  >
    <slot/>
    <span v-if="count !== undefined && count !== null" class="border border-current px-1 text-[10px]">{{ count }}</span>
  </button>
</template>

<style scoped>
.tab-on {
  background: var(--bg);
  color: var(--text);
  box-shadow: inset 0 2px 0 var(--color-primary);
}
</style>
