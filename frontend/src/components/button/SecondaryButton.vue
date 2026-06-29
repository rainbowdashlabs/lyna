/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseButton from './BaseButton.vue'

defineProps<{
  disabled?: boolean
  fullWidth?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <BaseButton
      :disabled="disabled"
      :full-width="fullWidth"
      class="bg-secondary text-secondary-text hover:bg-secondary-accent hover:text-secondary-accent-text"
      @click="$emit('click', $event)"
  >
    <slot/>
  </BaseButton>
</template>
