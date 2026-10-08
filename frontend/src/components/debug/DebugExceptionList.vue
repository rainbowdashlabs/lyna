/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {DebugSection} from '~/api/debug'
import {groupExceptions} from '~/util/debugLog'

/**
 * The exceptions the plugin collected, its own apart from everybody else's, each kind grouped and
 * counted. While a search runs only the groups it matches are listed.
 */
const props = defineProps<{
    exceptions: DebugSection[]
    contents: Map<number, string>
    pattern: RegExp | null
}>()

defineEmits<{
    locate: [title: string]
}>()

const {t} = useI18n()

function groupsOf(kind: DebugSection['kind']) {
    const groups = groupExceptions(props.exceptions
        .filter(section => section.kind === kind && props.contents.has(section.position))
        .map(section => ({position: section.position, content: props.contents.get(section.position)!})))
    const pattern = props.pattern
    if (!pattern) return groups
    return groups.filter(group => {
        pattern.lastIndex = 0
        return pattern.test(group.sample)
    })
}

const kinds = computed(() => [
    {label: t('ui.debugExceptionList.plugin'), groups: groupsOf('INTERNAL_EXCEPTION')},
    {label: t('ui.debugExceptionList.other'), groups: groupsOf('EXTERNAL_EXCEPTION')},
])
</script>

<template>
  <div class="space-y-6">
    <section v-for="kind in kinds" :key="kind.label" class="space-y-2">
      <SectionLabel>{{ kind.label }}</SectionLabel>
      <EmptyHint v-if="!kind.groups.length">{{ t('ui.debugExceptionList.none') }}</EmptyHint>
      <DebugExceptionGroup v-for="group in kind.groups" :key="group.title + group.origin" :group="group" :pattern="pattern" @locate="title => $emit('locate', title)"/>
    </section>
  </div>
</template>
