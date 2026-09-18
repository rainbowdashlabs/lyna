/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import NeutralContainer from '@/components/container/NeutralContainer.vue'
import THead from './THead.vue'

/**
 * Scrollable data table frame: horizontal scroll container, the shared table sizing and the
 * header row. The `head` slot takes the header cells, the default slot the body rows.
 * `plain` swaps the container for a bare scroll wrapper where the table already sits inside
 * a card.
 */
defineProps<{
  plain?: boolean
}>()
</script>

<template>
  <component :is="plain ? 'div' : NeutralContainer" class="overflow-x-auto">
    <table class="w-full text-sm">
      <thead>
        <THead>
          <slot name="head"/>
        </THead>
      </thead>
      <tbody>
        <slot/>
      </tbody>
    </table>
  </component>
</template>
