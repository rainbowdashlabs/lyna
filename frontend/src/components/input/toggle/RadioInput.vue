/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<string>()

defineProps<{
  value: string
  disabled?: boolean
}>()
</script>

<template>
  <input
      v-model="model"
      :value="value"
      :disabled="disabled"
      type="radio"
      class="accent-primary cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
  />
</template>
