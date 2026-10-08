/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

/**
 * How a log is shown, and where the reader is among the search's matches in it.
 */
defineProps<{
    matches: number
    current: number
}>()

defineEmits<{
    step: [by: number]
}>()

const problemsOnly = defineModel<boolean>('problemsOnly', {required: true})
const wrap = defineModel<boolean>('wrap', {required: true})

const {t} = useI18n()
</script>

<template>
  <div class="ml-auto flex flex-wrap items-center gap-4 text-xs">
    <label class="flex items-center gap-2"><CompactToggle v-model="problemsOnly"/> {{ t('ui.debugLog.problemsOnly') }}</label>
    <label class="flex items-center gap-2"><CompactToggle v-model="wrap"/> {{ t('ui.debugLog.wrap') }}</label>
    <span v-if="matches" class="font-data flex items-center gap-1">
      <MutedIconButton :icon="['fas', 'chevron-up']" :label="t('ui.debugLog.previousMatch')" @click="$emit('step', -1)"/>
      {{ t('ui.debugLog.matchPosition', {current: current + 1, count: matches}) }}
      <MutedIconButton :icon="['fas', 'chevron-down']" :label="t('ui.debugLog.nextMatch')" @click="$emit('step', 1)"/>
    </span>
  </div>
</template>
