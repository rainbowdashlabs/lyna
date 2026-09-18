/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
withDefaults(defineProps<{
  size?: 'xs' | 'sm'
  tag?: 'span' | 'p' | 'div'
}>(), {
  size: 'xs',
  tag: 'span',
})
</script>

<template>
  <component :is="tag" :class="size === 'sm' ? 'text-sm' : 'text-xs'" class="text-(--text-muted)">
    <slot/>
  </component>
</template>
