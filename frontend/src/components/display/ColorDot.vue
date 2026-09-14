/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Small round colour swatch used to mark groups, tags and other user-picked colours in lists.
 * The colour comes in as a CSS colour string so it can be whatever the user configured.
 */
defineProps<{
  color: string
}>()
</script>

<template>
  <span :style="{backgroundColor: color}" class="inline-block h-3 w-3 rounded-full"></span>
</template>
