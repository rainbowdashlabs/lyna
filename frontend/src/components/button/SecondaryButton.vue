/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
/*
 * Outlined rather than filled. A fill in this interface says something - blue that a thing is
 * granted, pink that it is waiting on somebody - and a button filled blue would be making that
 * claim about itself. The quiet ones take the page's hairline and colour only on hover.
 */
import BaseButton from './BaseButton.vue'

defineProps<{
  disabled?: boolean
  fullWidth?: boolean
  compact?: boolean
}>()

defineEmits<{
  click: [event: MouseEvent]
}>()
</script>

<template>
  <BaseButton
      :disabled="disabled"
      :full-width="fullWidth"
      :compact="compact"
      class="border-(--border) bg-transparent text-(--text) hover:border-secondary hover:bg-secondary/12"
      @click="$emit('click', $event)"
  >
    <slot/>
  </BaseButton>
</template>
