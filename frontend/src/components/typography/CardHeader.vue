/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
withDefaults(defineProps<{
    /** `error` marks a destructive section, such as account deletion. */
    tone?: 'muted' | 'error'
}>(), {
    tone: 'muted',
})
</script>

<template>
  <h2 :class="tone === 'error' ? 'text-error' : 'text-(--text-muted)'"
      class="font-data mb-3 text-[11px] tracking-[0.14em] uppercase">
    <slot/>
  </h2>
</template>
