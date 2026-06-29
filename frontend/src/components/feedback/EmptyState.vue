/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
withDefaults(defineProps<{
  compact?: boolean
}>(), {
  compact: false,
})
</script>

<template>
  <div :class="compact ? 'py-4' : 'py-8'" class="text-center text-(--text-muted)">
    <slot/>
  </div>
</template>
