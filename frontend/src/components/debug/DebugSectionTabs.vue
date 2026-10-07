/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {DebugTab} from '~/api/debug'

/**
 * The parts of a report. While a search runs, each tab says how many matches it holds.
 */
defineProps<{
    counts: Partial<Record<DebugTab, number>>
}>()

const tab = defineModel<DebugTab>({required: true})

const {t} = useI18n()

const TABS: DebugTab[] = ['overview', 'logs', 'exceptions', 'configs', 'plugins']
</script>

<template>
  <nav class="flex overflow-x-auto border-b border-border-light bg-bg-light-accent dark:border-border-dark dark:bg-bg-dark-accent" role="tablist">
    <TabButton v-for="name in TABS" :key="name" :active="tab === name" :count="counts[name]" @select="tab = name">
      {{ t(`ui.debugSectionTabs.${name}`) }}
    </TabButton>
  </nav>
</template>
