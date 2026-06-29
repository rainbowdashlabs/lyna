/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
defineProps<{
  disabled?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <button
      :disabled="disabled"
      class="text-xs text-primary hover:underline cursor-pointer font-normal transition-colors disabled:opacity-50 disabled:cursor-not-allowed"
      @click="$emit('click', $event)"
  >
    <slot/>
  </button>
</template>
