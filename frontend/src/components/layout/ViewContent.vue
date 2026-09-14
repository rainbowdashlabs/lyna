/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onBeforeUnmount, watch} from 'vue'
import {claimPageHeader} from '@/composables/usePageHeader'

const props = defineProps<{
  title: string
  subtitle?: string
}>()

const {set, release} = claimPageHeader()

watch(
    () => [props.title, props.subtitle ?? ''] as const,
    ([titleValue, subtitleValue]) => set(titleValue, subtitleValue),
    {immediate: true},
)

onBeforeUnmount(release)
</script>

<template>
  <div class="p-4 sm:p-6">
    <slot/>
  </div>
</template>
