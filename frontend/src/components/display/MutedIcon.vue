/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Muted leading icon. The `xs`/`sm`/`md` sizes pin the icon to a fixed box, `inline` lets it
 * scale with the surrounding text instead so it lines up with adjacent labels.
 */
defineProps<{
  icon: string[]
  size?: 'xs' | 'sm' | 'md' | 'inline'
}>()
</script>

<template>
  <font-awesome-icon
      :icon="icon"
      :class="{
        'h-2.5 w-2.5': size === 'xs',
        'h-3 w-3': !size || size === 'sm',
        'h-3.5 w-3.5': size === 'md',
        'text-xs': size === 'inline',
      }"
      class="text-(--text-muted)"
  />
</template>
