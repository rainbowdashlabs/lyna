/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {BORDERED_INPUT_CLASSES} from '../inputClasses'

const model = defineModel<string>()

defineProps<{
  placeholder?: string
  disabled?: boolean
  rows?: number
}>()
</script>

<template>
  <textarea
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :rows="rows ?? 4"
      :class="[BORDERED_INPUT_CLASSES, 'w-full resize-y']"
  />
</template>
