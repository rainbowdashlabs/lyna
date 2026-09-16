/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  block?: boolean
}>()
</script>

<template>
  <code :class="block ? 'block px-3 py-2 text-xs' : 'px-2 py-1'" class="font-data rounded-theme border border-(--border) bg-(--bg-accent)/30">
    <slot/>
  </code>
</template>
