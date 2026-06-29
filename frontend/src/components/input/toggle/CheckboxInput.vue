/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
const model = defineModel<boolean>({ default: false })

defineProps<{
  disabled?: boolean
}>()
</script>

<template>
  <input
    v-model="model"
    type="checkbox"
    :disabled="disabled"
    class="h-4 w-4 rounded accent-primary cursor-pointer disabled:opacity-50 disabled:cursor-not-allowed"
  />
</template>
