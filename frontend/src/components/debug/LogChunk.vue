/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed, onBeforeUnmount, onMounted, ref} from 'vue'
import type {LogBlock} from '~/util/debugLog'

/**
 * A run of a log's blocks that is drawn once it comes near the screen, and stays drawn.
 *
 * <p>Until then it is an empty box as tall as its lines would be, so the scrollbar is right. A chunk
 * holding the line being looked at is drawn straight away. This is what keeps a log of tens of
 * thousands of lines quick to open and to search: only what has been scrolled past is ever drawn,
 * and a search redraws only that. {@code content-visibility} would do the same, but WebKit paints such
 * rows blank.
 */
const props = defineProps<{
    blocks: LogBlock[]
    anchorPrefix: string
    pattern: RegExp | null
    target: number | null
    current: number | null
    wrap: boolean
    isOpen: (block: LogBlock) => boolean
}>()

defineEmits<{
    toggle: [block: LogBlock]
}>()

const LINE_HEIGHT_REM = 1.25

const root = ref<HTMLElement | null>(null)
const seen = ref(false)
let observer: IntersectionObserver | null = null

function holds(line: number | null) {
    if (line === null || !props.blocks.length) return false
    const first = props.blocks[0]!.head.number
    const last = props.blocks[props.blocks.length - 1]!
    const end = last.tail.length ? last.tail[last.tail.length - 1]!.number : last.head.number
    return line >= first && line <= end
}

const drawn = computed(() => seen.value || holds(props.target) || holds(props.current))
const estimate = computed(() => props.blocks.reduce((sum, block) => sum + 1 + (block.tail.length ? 1 : 0), 0) * LINE_HEIGHT_REM)

onMounted(() => {
    observer = new IntersectionObserver(entries => {
        if (entries.some(entry => entry.isIntersecting)) {
            seen.value = true
            observer?.disconnect()
        }
    }, {rootMargin: '1500px 0px'})
    if (root.value) observer.observe(root.value)
})

onBeforeUnmount(() => observer?.disconnect())
</script>

<template>
  <div ref="root" :style="drawn ? undefined : {height: `${estimate}rem`}">
    <template v-if="drawn">
      <LogBlockRow
          v-for="block in blocks"
          :key="block.head.number"
          :anchor-prefix="anchorPrefix"
          :block="block"
          :current="current"
          :open="isOpen(block)"
          :pattern="pattern"
          :target="target"
          :wrap="wrap"
          @toggle="$emit('toggle', block)"
      />
    </template>
  </div>
</template>
