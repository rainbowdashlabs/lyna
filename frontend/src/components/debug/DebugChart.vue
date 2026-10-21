/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'
import {useI18n} from 'vue-i18n'
import VChart from 'vue-echarts'
import type {DebugSection} from '~/api/debug'
import {useDarkClass} from '~/composables/useDarkClass'
import {normalizeChart} from '~/util/chartData'
import {chartOption} from '~/util/chartOption'
import {registerCharts} from '~/util/echarts'

/**
 * A chart the plugin sent. When its type or data cannot be drawn, the data is shown as it was sent.
 */
const props = defineProps<{
    section: DebugSection
}>()

registerCharts()
const {t} = useI18n()
const dark = useDarkClass()

const option = computed(() => props.section.chart ? chartOption(props.section.chart, normalizeChart(props.section.chart)) : null)
const raw = computed(() => JSON.stringify(props.section.chart?.data ?? null, null, 2))
</script>

<template>
  <section class="space-y-2">
    <SectionLabel>{{ section.name }}</SectionLabel>
    <ClientOnly v-if="option">
      <VChart :option="option" :theme="dark ? 'dark' : undefined" autoresize class="h-72 w-full"/>
    </ClientOnly>
    <div v-else class="space-y-1">
      <MutedText size="xs">{{ t('ui.debugChart.raw', {type: section.chart?.type ?? ''}) }}</MutedText>
      <pre class="font-data max-h-72 overflow-auto border border-border-light p-2 text-xs dark:border-border-dark">{{ raw }}</pre>
    </div>
  </section>
</template>
