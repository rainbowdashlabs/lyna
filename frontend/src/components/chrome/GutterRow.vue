/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
    /** What to show in the gutter. A position in the list, usually. */
    marker: string | number
    /** Marks the row as waiting on somebody, which is the only thing that fills a row. */
    pending?: boolean
}>()
</script>

<template>
  <div
      :class="pending ? 'pending-row bg-primary/12 shadow-[inset_3px_0_0_var(--color-primary)]' : ''"
      class="grid grid-cols-[2rem_1fr_auto] items-center gap-3 border-b border-border-light py-2 pr-2 dark:border-border-dark"
  >
    <span class="font-data pr-1 text-right text-[11px] text-(--text-muted) select-none">{{ marker }}</span>
    <div class="min-w-0"><slot/></div>
    <div><slot name="trailing"/></div>
  </div>
</template>

<style scoped>
/*
 * A hairline border disappears into the pink wash, which left the control on the row that needs
 * attention as the least visible one on the page. It takes the marker's own colour instead.
 */
.pending-row :deep(button) {
  border-color: var(--color-primary);
}
</style>
