/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import BaseInput from '../BaseInput.vue'

const model = defineModel<number>()

defineProps<{
  placeholder?: string
  disabled?: boolean
}>()
</script>

<template>
  <BaseInput
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      step="1"
      type="number"
  />
</template>
