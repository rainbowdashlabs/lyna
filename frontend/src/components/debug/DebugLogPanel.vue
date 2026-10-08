/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref, watch} from 'vue'
import type {DebugSection} from '~/api/debug'
import {atLeast, blocksOf, type LineTarget, matchingLines, splitLines} from '~/util/debugLog'

/**
 * The report's logs, one at a time, with the controls for reading one: which log, warnings and
 * errors only, wrapping, and stepping through what the search matched.
 */
const props = defineProps<{
    logs: DebugSection[]
    contents: Map<number, string>
    pattern: RegExp | null
    /** The log and line a link names. */
    target: LineTarget | null
}>()

const emit = defineEmits<{
    open: [position: number]
}>()

const {t} = useI18n()

const selected = ref(props.target?.position ?? props.logs[0]?.position ?? 0)
const problemsOnly = ref(false)
const wrap = ref(true)
const matchIndex = ref(0)

const lines = computed(() => {
    const content = props.contents.get(selected.value)
    return content === undefined ? null : splitLines(content)
})

const shown = computed(() => {
    if (!lines.value) return []
    if (!problemsOnly.value) return lines.value
    return blocksOf(lines.value)
        .filter(block => atLeast(block.level, 'warn'))
        .flatMap(block => [block.head, ...block.tail])
})

const matches = computed(() => matchingLines(shown.value, props.pattern))
const current = computed(() => matches.value.length ? matches.value[matchIndex.value % matches.value.length]! : null)
const target = computed(() => props.target?.position === selected.value ? props.target.line : null)

function step(by: number) {
    if (!matches.value.length) return
    matchIndex.value = (matchIndex.value + by + matches.value.length) % matches.value.length
}

watch(selected, position => emit('open', position), {immediate: true})
watch(() => props.target, next => {
    if (next) selected.value = next.position
})
watch([() => props.pattern, selected, problemsOnly], () => {
    matchIndex.value = 0
})
</script>

<template>
  <div class="space-y-3">
    <div class="flex flex-wrap items-center gap-2">
      <SelectionToggleButton
          v-for="log in logs"
          :key="log.position"
          :selected="selected === log.position"
          @toggle="selected = log.position"
      >
        {{ log.name }} · {{ t('ui.debugLog.lines', {count: log.lines}) }}
      </SelectionToggleButton>
      <DebugLogControls v-model:problems-only="problemsOnly" v-model:wrap="wrap" :current="matchIndex" :matches="matches.length" @step="step"/>
    </div>
    <Spinner v-if="!lines"/>
    <LogView
        v-else
        :anchor-prefix="`s${selected}`"
        :current="current"
        :floor="problemsOnly ? 'warn' : null"
        :lines="lines"
        :pattern="pattern"
        :target="target"
        :wrap="wrap"
    />
  </div>
</template>
