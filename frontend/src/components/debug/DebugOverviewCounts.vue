/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'

/**
 * The numbers worth knowing before reading anything, each a way into what it counts.
 */
defineProps<{
    /** Null until the log has been fetched. */
    errors: number | null
    warnings: number | null
    exceptions: number
    plugins: number
}>()

defineEmits<{
    show: [tab: 'logs' | 'exceptions' | 'plugins']
}>()

const {t} = useI18n()
</script>

<template>
  <div class="font-data grid grid-cols-2 gap-2 sm:grid-cols-4">
    <ChoiceButton :detail="t('ui.debugOverviewCounts.errors')" :title="errors === null ? '…' : String(errors)" @click="$emit('show', 'logs')"/>
    <ChoiceButton :detail="t('ui.debugOverviewCounts.warnings')" :title="warnings === null ? '…' : String(warnings)" @click="$emit('show', 'logs')"/>
    <ChoiceButton :detail="t('ui.debugOverviewCounts.exceptions')" :title="String(exceptions)" @click="$emit('show', 'exceptions')"/>
    <ChoiceButton :detail="t('ui.debugOverviewCounts.plugins')" :title="String(plugins)" @click="$emit('show', 'plugins')"/>
  </div>
</template>
