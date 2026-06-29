/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseContainer from './BaseContainer.vue'

const props = withDefaults(defineProps<{
  padded?: boolean
  clickable?: boolean
}>(), {
  padded: true,
})
</script>

<template>
  <BaseContainer
      :padded="props.padded"
      :class="clickable ? 'cursor-pointer hover:border-primary transition-colors' : ''"
      class="border-bg-light-accent bg-bg-light-accent/20 dark:border-bg-dark-accent dark:bg-bg-dark-accent/20">
    <slot/>
  </BaseContainer>
</template>
