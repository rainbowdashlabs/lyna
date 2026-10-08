/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import type {DebugTab} from '~/api/debug'
import {useDebugReport} from '~/composables/useDebugReport'
import {useDebugSearch} from '~/composables/useDebugSearch'
import {useDebugPreview} from '~/composables/useDebugPreview'
import type {LineTarget} from '~/util/debugLog'
import {formatDate} from '~/util/format'

/**
 * A debug report, at the address UpdateButler printed into the server console, so every link a
 * plugin ever handed out opens here.
 *
 * <p>A line is linked as {@code #s<section>-L<line>}; opening such a link goes straight to it.
 */
const {t} = useI18n()
const route = useRoute()
const readKey = String(route.params.key)
useDebugPreview(readKey)

const {report, contents, loading, notFound, error, load, content, loadAll, logs, exceptions, configs, metas} = useDebugReport(readKey)
const {query, pattern, invalid, counts} = useDebugSearch(report, contents)

const tab = ref<DebugTab>('overview')
const target = ref<LineTarget | null>(null)
const searching = ref(false)

function readHash(hash: string) {
    const link = /^#s(\d+)-L(\d+)$/.exec(hash)
    if (!link) return
    target.value = {position: Number(link[1]), line: Number(link[2])}
    tab.value = 'logs'
}

/**
 * Goes to the first line of a log that mentions an exception, so it can be read with what happened
 * around it.
 */
function locate(title: string) {
    for (const log of logs.value) {
        const lines = (contents.get(log.position) ?? '').split(/\r?\n/)
        const index = lines.findIndex(line => line.includes(title))
        if (index >= 0) {
            target.value = {position: log.position, line: index + 1}
            tab.value = 'logs'
            return
        }
    }
    query.text = title
    tab.value = 'logs'
}

const status = computed(() => report.value
    ? [{label: t('page.debug.read.expires', {date: formatDate(report.value.expires)})}, {label: t('page.debug.read.sections', {count: report.value.sections.length})}]
    : [])

watch(() => query.text, async text => {
    if (!text || searching.value) return
    searching.value = true
    await loadAll()
    searching.value = false
})
watch(() => route.hash, readHash)

onMounted(async () => {
    await load()
    await Promise.all([...exceptions.value, ...configs.value, ...metas.value, ...logs.value.slice(0, 1)]
        .map(section => content(section.position)))
    readHash(route.hash)
})
</script>

<template>
  <div class="flex flex-1 flex-col">
    <main class="flex-1 pb-12">
      <section class="mx-auto max-w-7xl space-y-6 px-4 py-6">
        <EmptyState v-if="notFound" :message="t('page.debug.read.notFound')"/>
        <AsyncSection v-else :error="error" :loading="loading">
          <template v-if="report">
            <DebugReportHeader :report="report"/>
            <DebugSearchBar v-model="query" :invalid="invalid" :searching="searching" class="sticky top-0 z-10 bg-(--bg) py-2"/>
            <DebugSectionTabs v-model="tab" :counts="counts"/>
            <DebugReportBody
                v-model="tab"
                :configs="configs"
                :contents="contents"
                :exceptions="exceptions"
                :logs="logs"
                :metas="metas"
                :pattern="pattern"
                :report="report"
                :target="target"
                @locate="locate"
                @open="content"
            />
          </template>
        </AsyncSection>
      </section>
    </main>
    <StatusBar :left="status"/>
  </div>
</template>
