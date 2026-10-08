/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {DebugReport, DebugSection} from '~/api/debug'
import {atLeast, blocksOf, splitLines} from '~/util/debugLog'

/**
 * What to look at first: how many problems the log holds, what the plugin says about itself, and
 * whatever extra blocks it added. A count opens the part of the report it counts.
 */
const props = defineProps<{
    report: DebugReport
    logs: DebugSection[]
    metas: DebugSection[]
    exceptions: number
    contents: Map<number, string>
}>()

defineEmits<{
    show: [tab: 'logs' | 'exceptions' | 'plugins']
}>()

const {t} = useI18n()

const problems = computed(() => {
    const log = props.logs[0]
    const content = log ? props.contents.get(log.position) : undefined
    if (content === undefined) return null
    const blocks = blocksOf(splitLines(content))
    return {
        errors: blocks.filter(block => atLeast(block.level, 'error')).length,
        warnings: blocks.filter(block => block.level === 'warn').length,
    }
})

const meta = computed(() => {
    const plugin = props.report.pluginMeta
    return [
        {label: t('ui.debugOverview.main'), value: plugin.main},
        {label: t('common.authors'), value: plugin.authors?.join(', ')},
        {label: t('common.dependsOn'), value: plugin.dependencies?.join(', ')},
        {label: t('ui.debugOverview.softDependencies'), value: plugin.softDependencies?.join(', ')},
        {label: t('ui.debugOverview.loadBefore'), value: plugin.loadBefore?.join(', ')},
    ].filter(entry => entry.value)
})
</script>

<template>
  <div class="space-y-8">
    <DebugOverviewCounts
        :errors="problems?.errors ?? null"
        :exceptions="exceptions"
        :plugins="report.serverMeta.plugins?.length ?? 0"
        :warnings="problems?.warnings ?? null"
        @show="tab => $emit('show', tab)"
    />
    <dl v-if="meta.length" class="grid gap-x-6 gap-y-3 sm:grid-cols-2">
      <div v-for="entry in meta" :key="entry.label">
        <dt><DetailLabel>{{ entry.label }}</DetailLabel></dt>
        <dd class="font-data text-sm break-all">{{ entry.value }}</dd>
      </div>
    </dl>
    <section v-for="section in metas" :key="section.position" class="space-y-2">
      <SectionLabel>{{ section.name }}</SectionLabel>
      <pre class="font-data overflow-x-auto border border-border-light p-3 text-xs dark:border-border-dark">{{ contents.get(section.position) }}</pre>
    </section>
  </div>
</template>
