/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {type DebugReport, getLatestVersion, type LatestVersion} from '~/api/debug'
import {formatDate, formatTime} from '~/util/format'

/**
 * Which plugin the report is about and the server it came from, at a glance, and whether a newer
 * version of the plugin is out, when it is a product here. That hint is fetched on its own, so a
 * slow or failing lookup never holds up the report.
 */
const props = defineProps<{
    report: DebugReport
    readKey: string
}>()

const {t} = useI18n()

const latest = ref<LatestVersion | null>(null)

onMounted(async () => {
    try {
        latest.value = await getLatestVersion(props.readKey)
    } catch {
        latest.value = null
    }
})

const facts = computed(() => [
    {label: t('ui.debugReportHeader.server'), value: props.report.serverMeta.version ?? '-'},
    {label: t('ui.debugReportHeader.players'), value: String(props.report.serverMeta.currentPlayers ?? '-')},
    {label: t('ui.debugReportHeader.worlds'), value: (props.report.serverMeta.loadedWorlds ?? []).join(', ') || '-'},
    {label: t('ui.debugReportHeader.uploaded'), value: `${formatDate(props.report.created)} ${formatTime(props.report.created)}`},
    {label: t('ui.debugReportHeader.expires'), value: formatDate(props.report.expires)},
])
</script>

<template>
  <header class="space-y-4">
    <div class="flex flex-wrap items-baseline gap-x-3">
      <Heading :level="1">{{ report.pluginName }}</Heading>
      <span class="font-data text-lg text-(--text-muted)">{{ report.pluginVersion }}</span>
    </div>
    <Alert v-if="latest?.outdated" variant="info">
      {{ t('ui.debugReportHeader.outdated', {latest: latest.version, reported: report.pluginVersion}) }}
    </Alert>
    <dl class="grid grid-cols-2 gap-x-6 gap-y-2 sm:grid-cols-5">
      <div v-for="fact in facts" :key="fact.label" class="min-w-0">
        <dt><DetailLabel>{{ fact.label }}</DetailLabel></dt>
        <dd class="font-data truncate text-sm" :title="fact.value">{{ fact.value }}</dd>
      </div>
    </dl>
  </header>
</template>
