/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  size?: 'sm' | 'md' | 'lg'
}>()
</script>

<template>
  <font-awesome-icon
      :class="[
      size === 'sm' ? 'text-base' : size === 'lg' ? 'text-3xl' : 'text-xl',
      'text-primary'
    ]"
      :icon="['fas', 'spinner']"
      spin
  />
</template>
