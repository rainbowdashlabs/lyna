/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseInput from '../BaseInput.vue'

const model = defineModel<number>()

const props = withDefaults(defineProps<{
  placeholder?: string
  disabled?: boolean
  step?: string
}>(), {
  step: '0.01',
})
</script>

<template>
  <BaseInput
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :step="props.step"
      type="number"
  />
</template>
