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
    <DebugConfigFile
        v-for="section in shown"
        :key="`${section.position}-${pattern !== null}`"
        :content="contents.get(section.position)!"
        :pattern="pattern"
        :section="section"
        :start-open="pattern !== null || configs.length === 1"
    />
  </div>
</template>
