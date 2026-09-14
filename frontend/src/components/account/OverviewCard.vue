/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
    title: string
}>()
</script>

<template>
  <NeutralContainer>
    <CardHeader>{{ title }}</CardHeader>
    <slot/>
  </NeutralContainer>
</template>
