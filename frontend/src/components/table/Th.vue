/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  align?: 'left' | 'center' | 'right'
}>()
</script>

<template>
  <th :class="align === 'center' ? 'text-center' : align === 'right' ? 'text-right' : ''" class="font-data px-3 py-2 text-[10px] tracking-[0.12em] text-(--text-muted) uppercase">
    <slot/>
  </th>
</template>
