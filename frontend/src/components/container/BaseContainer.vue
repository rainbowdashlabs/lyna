/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const props = withDefaults(defineProps<{
  padded?: boolean
}>(), {
  padded: true,
})
</script>

<template>
  <div
      :class="props.padded ? 'p-4' : ''"
      class="rounded-theme border transition-colors duration-150"
  >
    <slot/>
  </div>
</template>
