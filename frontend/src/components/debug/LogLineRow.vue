/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {LogLevel, LogLine} from '~/util/debugLog'

/**
 * One line of a log: its number, which links to it, and its text in the colour of its level.
 */
defineProps<{
    line: LogLine
    /** The level it is shown at, which for a stack frame is the level of the line that logged it. */
    level: LogLevel | null
    anchor: string
    pattern: RegExp | null
    targeted: boolean
    current: boolean
    wrap: boolean
}>()

const LEVEL_CLASS: Record<LogLevel, string> = {
    error: 'text-error shadow-[inset_2px_0_0_var(--color-error)]',
    warn: 'text-info shadow-[inset_2px_0_0_var(--color-info)]',
    info: '',
    debug: 'text-(--text-muted)',
}
</script>

<template>
  <div
      :id="anchor"
      :class="[level ? LEVEL_CLASS[level] : '', targeted ? 'bg-primary/12' : '', current ? 'outline outline-1 outline-primary' : '']"
      class="log-row grid grid-cols-[4.5rem_1fr] text-xs leading-5"
  >
    <a :href="`#${anchor}`" class="font-data pr-3 text-right text-[11px] text-(--text-muted) select-none hover:text-primary">{{ line.number }}</a>
    <LogLineText :class="wrap ? 'break-all whitespace-pre-wrap' : 'whitespace-pre'" :pattern="pattern" :text="line.text" class="font-data"/>
  </div>
</template>

<style scoped>
.log-row {
  content-visibility: auto;
  contain-intrinsic-size: auto 1.25rem;
  scroll-margin-top: 8rem;
}
</style>
