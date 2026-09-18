/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {BORDERED_INPUT_CLASSES} from '../inputClasses'

const model = defineModel<string | number | null>()

defineProps<{
  disabled?: boolean
}>()
</script>

<template>
  <select
      v-model="model"
      :disabled="disabled"
      :class="[BORDERED_INPUT_CLASSES, 'min-w-0']"
  >
    <slot/>
  </select>
</template>
