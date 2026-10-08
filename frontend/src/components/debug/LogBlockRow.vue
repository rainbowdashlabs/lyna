/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {LogBlock} from '~/util/debugLog'

/**
 * A logged line and the lines folded under it, such as the frames of its stack trace.
 */
defineProps<{
    block: LogBlock
    anchorPrefix: string
    pattern: RegExp | null
    open: boolean
    target: number | null
    current: number | null
    wrap: boolean
}>()

defineEmits<{
    toggle: []
}>()

const {t} = useI18n()
</script>

<template>
  <div>
    <div class="relative">
      <LogLineRow
          :anchor="`${anchorPrefix}-L${block.head.number}`"
          :current="current === block.head.number"
          :level="block.level"
          :line="block.head"
          :pattern="pattern"
          :targeted="target === block.head.number"
          :wrap="wrap"
      />
      <span v-if="block.tail.length" class="absolute top-0 left-0">
        <MutedIconButton
            :icon="['fas', open ? 'chevron-down' : 'chevron-right']"
            :label="open ? t('ui.debugLog.fold') : t('ui.debugLog.unfold', {count: block.tail.length})"
            @click="$emit('toggle')"
        />
      </span>
    </div>
    <template v-if="open">
      <LogLineRow
          v-for="line in block.tail"
          :key="line.number"
          :anchor="`${anchorPrefix}-L${line.number}`"
          :current="current === line.number"
          :level="block.level"
          :line="line"
          :pattern="pattern"
          :targeted="target === line.number"
          :wrap="wrap"
      />
    </template>
    <MutedText v-else-if="block.tail.length" class="font-data block pl-[4.5rem] text-[11px]" size="xs" tag="p">
      {{ t('ui.debugLog.folded', {count: block.tail.length}) }}
    </MutedText>
  </div>
</template>
