/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import type {DebugSection} from '~/api/debug'

/**
 * One configuration file the plugin sent, folded to its path until opened.
 */
const props = defineProps<{
    section: DebugSection
    content: string
    pattern: RegExp | null
    startOpen: boolean
}>()

const {t} = useI18n()
const open = ref(props.startOpen)
const lines = computed(() => props.content.split(/\r?\n/))
</script>

<template>
  <div class="border border-border-light dark:border-border-dark">
    <div class="flex items-center gap-3 bg-bg-light-accent px-3 py-1.5 dark:bg-bg-dark-accent">
      <MutedIconButton :icon="['fas', open ? 'chevron-down' : 'chevron-right']" :label="t('ui.debugConfigFile.toggle')" @click="open = !open"/>
      <span class="font-data min-w-0 flex-1 truncate text-sm">{{ section.name }}</span>
      <MutedText class="font-data" size="xs">{{ t('ui.debugLog.lines', {count: section.lines}) }}</MutedText>
    </div>
    <div v-if="open" class="overflow-x-auto py-1">
      <ConfigLine v-for="(line, index) in lines" :key="index" :number="index + 1" :pattern="pattern" :text="line"/>
    </div>
  </div>
</template>
