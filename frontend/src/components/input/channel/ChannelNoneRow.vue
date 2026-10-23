/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * The line at the top of an optional channel list that picks no channel at all.
 */
defineProps<{ label: string, selected: boolean, active: boolean }>()
const emit = defineEmits<{ select: [] }>()
</script>

<template>
  <button
      :aria-selected="selected"
      :class="selected ? 'bg-secondary/12 text-(--text) shadow-[inset_3px_0_0_var(--color-secondary)]'
        : active ? 'bg-(--bg-accent) text-(--text)' : 'text-(--text-muted) hover:bg-(--bg-accent) hover:text-(--text)'"
      :tabindex="-1"
      class="mb-1 flex w-full items-center gap-2 border-b border-(--border) py-1.5 pr-2 pl-6 text-left text-sm italic"
      role="option"
      type="button"
      @click="emit('select')"
      @mousedown.prevent
  >
    {{ label }}
  </button>
</template>
