/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, nextTick, reactive, watch} from 'vue'
import {atLeast, blocksOf, type LogBlock, type LogLevel, type LogLine} from '~/util/debugLog'

/**
 * A log, folded into blocks.
 *
 * <p>A block opens on its own when something in it is being looked at - the line a link names, or the
 * match the search is on - so neither is ever hidden inside a fold. The blocks are drawn in chunks as
 * they come near the screen.
 */
const props = defineProps<{
    lines: LogLine[]
    anchorPrefix: string
    pattern: RegExp | null
    /** Hides every block below this level. */
    floor: LogLevel | null
    target: number | null
    current: number | null
    wrap: boolean
}>()

const blocks = computed(() => blocksOf(props.lines))
const visible = computed(() => props.floor
    ? blocks.value.filter(block => atLeast(block.level, props.floor!))
    : blocks.value)

const CHUNK = 400
const chunks = computed(() => {
    const out: LogBlock[][] = []
    for (let start = 0; start < visible.value.length; start += CHUNK) out.push(visible.value.slice(start, start + CHUNK))
    return out
})

const opened = reactive(new Set<number>())

function holds(block: LogBlock, line: number | null) {
    return line !== null && block.tail.some(tail => tail.number === line)
}

function isOpen(block: LogBlock) {
    return opened.has(block.head.number) || holds(block, props.target) || holds(block, props.current)
}

function toggle(block: LogBlock) {
    if (opened.has(block.head.number)) opened.delete(block.head.number)
    else opened.add(block.head.number)
}

function reveal(line: number | null) {
    if (line === null) return
    nextTick(() => requestAnimationFrame(() =>
        document.getElementById(`${props.anchorPrefix}-L${line}`)?.scrollIntoView({block: 'center'})))
}

watch(() => props.current, reveal)
watch(() => props.target, reveal, {immediate: true})
</script>

<template>
  <div class="overflow-x-auto border border-border-light py-1 dark:border-border-dark">
    <LogChunk
        v-for="chunk in chunks"
        :key="`${chunk[0]!.head.number}-${chunk.length}`"
        :anchor-prefix="anchorPrefix"
        :blocks="chunk"
        :current="current"
        :is-open="isOpen"
        :pattern="pattern"
        :target="target"
        :wrap="wrap"
        @toggle="toggle"
    />
  </div>
</template>
