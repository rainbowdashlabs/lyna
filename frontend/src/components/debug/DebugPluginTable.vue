/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {PluginMeta} from '~/api/debug'

/**
 * Every plugin on the server, by name. While a search runs only the plugins it matches are listed.
 */
const props = defineProps<{
    plugins: PluginMeta[]
    pattern: RegExp | null
}>()

const {t} = useI18n()

function describe(plugin: PluginMeta) {
    return [plugin.name, plugin.version, ...(plugin.authors ?? [])].join(' ')
}

const shown = computed(() => [...props.plugins]
    .sort((a, b) => a.name.localeCompare(b.name))
    .filter(plugin => {
        if (!props.pattern) return true
        props.pattern.lastIndex = 0
        return props.pattern.test(describe(plugin))
    }))
</script>

<template>
  <EmptyHint v-if="!shown.length">{{ t('ui.debugPluginTable.none') }}</EmptyHint>
  <DataTable v-else>
    <template #head>
      <Th>{{ t('ui.debugPluginTable.name') }}</Th>
      <Th>{{ t('ui.debugPluginTable.version') }}</Th>
      <Th>{{ t('ui.debugPluginTable.state') }}</Th>
      <Th>{{ t('common.authors') }}</Th>
      <Th>{{ t('common.dependsOn') }}</Th>
    </template>
    <TRow v-for="plugin in shown" :key="plugin.name">
      <Td><LogLineText :pattern="pattern" :text="plugin.name" class="font-data"/></Td>
      <Td><LogLineText :pattern="pattern" :text="plugin.version ?? '-'" class="font-data"/></Td>
      <Td :class="plugin.enabled === false ? 'text-error' : ''">
        {{ plugin.enabled === false ? t('ui.debugPluginTable.disabled') : t('ui.debugPluginTable.enabled') }}
      </Td>
      <Td>{{ (plugin.authors ?? []).join(', ') }}</Td>
      <Td class="font-data text-xs">{{ [...(plugin.dependencies ?? []), ...(plugin.softDependencies ?? [])].join(', ') }}</Td>
    </TRow>
  </DataTable>
</template>
