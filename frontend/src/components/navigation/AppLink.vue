/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  href: string
  icon?: string[]
  external?: boolean
}>()
</script>

<template>
  <a
      :href="href"
      :rel="external ? 'noopener noreferrer' : undefined"
      :target="external ? '_blank' : undefined"
      class="inline-flex items-center gap-1.5 text-[var(--link)] hover:underline"
  >
    <font-awesome-icon v-if="icon" :icon="icon" class="shrink-0"/>
    <slot/>
  </a>
</template>
