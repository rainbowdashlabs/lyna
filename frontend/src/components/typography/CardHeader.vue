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
      class="mb-3 text-sm font-semibold tracking-wider uppercase">
    <slot/>
  </h2>
</template>
