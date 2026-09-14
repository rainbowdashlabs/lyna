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
  <th :class="align === 'center' ? 'text-center' : align === 'right' ? 'text-right' : ''" class="px-3 py-2 font-medium">
    <slot/>
  </th>
</template>
