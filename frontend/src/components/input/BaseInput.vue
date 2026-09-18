/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {BORDERED_INPUT_CLASSES, BORDERLESS_INPUT_CLASSES} from './inputClasses'

const model = defineModel<string | number>()

const props = defineProps<{
  type?: 'text' | 'number' | 'date' | 'time' | 'datetime-local' | 'password' | 'email'
  placeholder?: string
  disabled?: boolean
  step?: string
  borderless?: boolean
}>()
</script>

<template>
  <input
      v-model="model"
      :disabled="disabled"
      :placeholder="placeholder"
      :step="step"
      :type="type ?? 'text'"
      :class="['w-full', props.borderless ? BORDERLESS_INPUT_CLASSES : BORDERED_INPUT_CLASSES]"
  />
</template>
