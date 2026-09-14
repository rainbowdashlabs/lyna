/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Table body cell. `dense` drops the leading padding for the compact report tables whose first
 * column already sits flush with the surrounding card.
 */
defineProps<{
  muted?: boolean
  align?: 'left' | 'center' | 'right'
  dense?: boolean
}>()
</script>

<template>
  <td
      :class="[
        dense ? 'pr-3' : 'px-3',
        muted ? 'text-(--text-muted)' : '',
        align === 'center' ? 'text-center' : align === 'right' ? 'text-right' : '',
      ]"
      class="py-2"
  >
    <slot/>
  </td>
</template>
