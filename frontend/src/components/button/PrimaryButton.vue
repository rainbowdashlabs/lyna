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
      class="bg-primary text-primary-text hover:bg-primary-accent hover:text-primary-accent-text"
      @click="$emit('click', $event)"
  >
    <slot/>
  </BaseButton>
</template>
