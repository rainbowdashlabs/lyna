/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import type {DebugSection} from '~/api/debug'
import {formatOf} from '~/util/debugLog'

/**
 * One configuration file the plugin sent, folded to its name until opened; the list names its folder.
 */
const props = defineProps<{
    section: DebugSection
    content: string
    pattern: RegExp | null
    startOpen: boolean
}>()

const {t} = useI18n()
const open = ref(props.startOpen)
const fileName = computed(() => props.section.name.split('/').pop() ?? props.section.name)
const lines = computed(() => props.content.split(/\r?\n/))
const format = computed(() => formatOf(props.section))
</script>

<template>
  <div class="border border-border-light dark:border-border-dark">
    <div class="flex items-center gap-3 bg-bg-light-accent px-3 py-1.5 dark:bg-bg-dark-accent">
      <MutedIconButton :icon="['fas', open ? 'chevron-down' : 'chevron-right']" :label="t('ui.debugConfigFile.toggle')" @click="open = !open"/>
      <span class="font-data min-w-0 flex-1 truncate text-sm">{{ fileName }}</span>
      <MutedText class="font-data" size="xs">{{ t('ui.debugLog.lines', {count: section.lines}) }}</MutedText>
    </div>
    <div v-if="open" class="overflow-x-auto py-1">
      <ConfigLine v-for="(line, index) in lines" :key="index" :format="format" :number="index + 1" :pattern="pattern" :text="line"/>
    </div>
  </div>
</template>
