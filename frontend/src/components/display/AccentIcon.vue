/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/**
 * Accent-coloured leading icon for feature and bullet rows. Sibling of `MutedIcon`: same prop
 * shape, fixed box, aligned with the first line of the text it introduces.
 */
defineProps<{
  icon: string[]
}>()
</script>

<template>
  <font-awesome-icon :icon="icon" class="h-4 w-4 text-primary mt-0.5 shrink-0"/>
</template>
