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
 * match the search is on - so neither is ever hidden inside a fold. Rows are left to the browser to
 * skip while off screen, which is what keeps a log of tens of thousands of lines scrollable.
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
    <LogBlockRow
        v-for="block in visible"
        :key="block.head.number"
        :anchor-prefix="anchorPrefix"
        :block="block"
        :current="current"
        :open="isOpen(block)"
        :pattern="pattern"
        :target="target"
        :wrap="wrap"
        @toggle="toggle(block)"
    />
  </div>
</template>
