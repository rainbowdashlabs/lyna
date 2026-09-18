/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'

const props = withDefaults(defineProps<{
  name: string
  /** `sm` for a row, the default for a storefront tile. */
  size?: 'sm' | 'md'
}>(), {
  size: 'md',
})

const initials = computed(() => {
  const trimmed = props.name.trim()
  if (!trimmed) return '??'
  const parts = trimmed.split(/\s+/).filter(Boolean)
  if (parts.length === 1) return parts[0]!.slice(0, 2).toUpperCase()
  return (parts[0]![0]! + parts[1]![0]!).toUpperCase()
})
</script>

<template>
  <div
      :class="size === 'sm' ? 'h-10 w-10 text-base' : 'h-16 w-16 text-2xl'"
      aria-hidden="true"
      class="font-data rounded-theme flex items-center justify-center border border-(--border) bg-(--bg-accent) font-bold text-(--text-muted) select-none"
  >
    {{ initials }}
  </div>
</template>
