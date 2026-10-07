/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DebugReport, DebugSection, DebugTab} from '~/api/debug'
import type {LineTarget} from '~/util/debugLog'

/**
 * The part of a report the current tab shows.
 */
defineProps<{
    report: DebugReport
    contents: Map<number, string>
    pattern: RegExp | null
    logs: DebugSection[]
    exceptions: DebugSection[]
    configs: DebugSection[]
    metas: DebugSection[]
    target: LineTarget | null
}>()

defineEmits<{
    open: [position: number]
    locate: [title: string]
}>()

const tab = defineModel<DebugTab>({required: true})
</script>

<template>
  <DebugOverview
      v-if="tab === 'overview'"
      :contents="contents"
      :exceptions="exceptions.length"
      :logs="logs"
      :metas="metas"
      :report="report"
      @show="next => tab = next"
  />
  <DebugLogPanel v-else-if="tab === 'logs'" :contents="contents" :logs="logs" :pattern="pattern" :target="target" @open="position => $emit('open', position)"/>
  <DebugExceptionList v-else-if="tab === 'exceptions'" :contents="contents" :exceptions="exceptions" :pattern="pattern" @locate="title => $emit('locate', title)"/>
  <DebugConfigList v-else-if="tab === 'configs'" :configs="configs" :contents="contents" :pattern="pattern"/>
  <DebugPluginTable v-else :pattern="pattern" :plugins="report.serverMeta.plugins ?? []"/>
</template>
