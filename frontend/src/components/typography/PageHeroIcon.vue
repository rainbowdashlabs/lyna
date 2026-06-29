/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  icon: [string, string]
}>()
</script>

<template>
  <font-awesome-icon :icon="icon" class="text-4xl text-primary mb-3"/>
</template>
