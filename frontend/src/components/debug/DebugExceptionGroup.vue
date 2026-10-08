/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import type {ExceptionGroup} from '~/util/debugLog'

/**
 * Exceptions that read alike, shown once with how often they happened. Opens to the first of them
 * in full.
 */
defineProps<{
    group: ExceptionGroup
    pattern: RegExp | null
}>()

defineEmits<{
    locate: [title: string]
}>()

const {t} = useI18n()
const open = ref(false)
</script>

<template>
  <GutterRow :marker="`${group.count}×`">
    <div class="space-y-1">
      <LogLineText :pattern="pattern" :text="group.title" class="font-data block text-sm break-all text-error"/>
      <MutedText v-if="group.origin" class="font-data block break-all" size="xs">{{ group.origin }}</MutedText>
      <pre v-if="open" class="font-data overflow-x-auto py-2 text-xs whitespace-pre"><LogLineText :pattern="pattern" :text="group.sample"/></pre>
    </div>
    <template #trailing>
      <span class="flex items-center gap-1">
        <MutedIconButton :icon="['fas', 'magnifying-glass']" :label="t('ui.debugExceptionGroup.findInLog')" @click="$emit('locate', group.title)"/>
        <MutedIconButton :icon="['fas', open ? 'chevron-down' : 'chevron-right']" :label="t('ui.debugExceptionGroup.toggleTrace')" @click="open = !open"/>
      </span>
    </template>
  </GutterRow>
</template>
