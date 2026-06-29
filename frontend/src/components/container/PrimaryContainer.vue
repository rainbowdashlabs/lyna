/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseContainer from './BaseContainer.vue'

const props = withDefaults(defineProps<{
  padded?: boolean
}>(), {
  padded: true,
})
</script>

<template>
  <BaseContainer :padded="props.padded" class="border-primary bg-primary/10">
    <slot/>
  </BaseContainer>
</template>
