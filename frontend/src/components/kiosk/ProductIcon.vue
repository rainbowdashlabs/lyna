/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref, watch} from 'vue'

const props = defineProps<{
    name: string
    iconUrl: string | null
    size?: 'sm' | 'md'
}>()

/**
 * A hosted icon that has stopped loading falls back to the generated monogram rather than to the
 * browser's broken-image mark. Operators host these themselves, so an address going away is a
 * normal thing to survive rather than an error to show.
 */
const failed = ref(false)
watch(() => props.iconUrl, () => {
    failed.value = false
})
</script>

<template>
  <img
      v-if="iconUrl && !failed"
      :alt="name"
      :class="size === 'sm' ? 'h-10 w-10' : 'h-16 w-16'"
      :src="iconUrl"
      class="rounded-theme object-contain"
      @error="failed = true"
  />
  <ProductMonogram v-else :name="name" :size="size"/>
</template>
