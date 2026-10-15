/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {CheckLevel, DebugSection} from '~/api/debug'

/**
 * The health checks the plugin ran, worst first, each with what it found.
 */
const props = defineProps<{
    checks: DebugSection[]
    contents: Map<number, string>
}>()

const {t} = useI18n()

const ORDER: Record<CheckLevel, number> = {ERROR: 0, WARN: 1, OK: 2}
const LOOK: Record<CheckLevel, {icon: string, colour: string}> = {
    ERROR: {icon: 'circle-xmark', colour: 'text-error'},
    WARN: {icon: 'circle-exclamation', colour: 'text-info'},
    OK: {icon: 'circle-check', colour: 'text-success'},
}

const sorted = computed(() => [...props.checks]
    .sort((a, b) => ORDER[a.level ?? 'OK'] - ORDER[b.level ?? 'OK']))
</script>

<template>
  <section class="space-y-2">
    <SectionLabel>{{ t('ui.debugCheckList.title') }}</SectionLabel>
    <ul>
      <li v-for="check in sorted" :key="check.position" class="flex items-start gap-3 border-b border-border-light py-2 dark:border-border-dark">
        <font-awesome-icon :class="LOOK[check.level ?? 'OK'].colour" :icon="['fas', LOOK[check.level ?? 'OK'].icon]" class="mt-0.5"/>
        <div class="min-w-0">
          <span class="text-sm font-semibold">{{ check.name }}</span>
          <MutedText class="font-data block break-all" size="xs">{{ contents.get(check.position) }}</MutedText>
        </div>
      </li>
    </ul>
  </section>
</template>
