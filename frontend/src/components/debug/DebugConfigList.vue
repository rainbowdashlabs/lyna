/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {DebugSection} from '~/api/debug'

/**
 * The plugin's configuration files. While a search runs only the files it matches are listed, and
 * they are open.
 */
const props = defineProps<{
    configs: DebugSection[]
    contents: Map<number, string>
    pattern: RegExp | null
}>()

const {t} = useI18n()

/**
 * The shown files by the folder they are in, the plugin's own folder first, then the others by path.
 */
const folders = computed(() => {
    const byFolder = new Map<string, DebugSection[]>()
    for (const section of [...shown.value].sort((a, b) => a.name.localeCompare(b.name))) {
        const slash = section.name.lastIndexOf('/')
        const folder = slash >= 0 ? section.name.slice(0, slash) : ''
        byFolder.set(folder, [...byFolder.get(folder) ?? [], section])
    }
    return [...byFolder.entries()]
        .sort(([a], [b]) => a.localeCompare(b))
        .map(([path, sections]) => ({path, sections}))
})

const shown = computed(() => props.configs.filter(section => {
    const content = props.contents.get(section.position)
    if (content === undefined) return false
    if (!props.pattern) return true
    props.pattern.lastIndex = 0
    return props.pattern.test(content)
}))
</script>

<template>
  <div class="space-y-3">
    <EmptyHint v-if="!shown.length">{{ t('ui.debugConfigList.none') }}</EmptyHint>
    <section v-for="folder in folders" :key="folder.path" class="space-y-2">
      <SectionLabel v-if="folder.path" class="font-data">{{ folder.path }}/</SectionLabel>
      <DebugConfigFile
          v-for="section in folder.sections"
          :key="`${section.position}-${pattern !== null}`"
          :content="contents.get(section.position)!"
          :pattern="pattern"
          :section="section"
          :start-open="pattern !== null || configs.length === 1"
      />
    </section>
  </div>
</template>
