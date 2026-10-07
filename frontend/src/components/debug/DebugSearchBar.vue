/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {SearchQuery} from '~/util/debugLog'

/**
 * One search across the whole report. A regex that does not compile is said beside the box rather
 * than searched for.
 */
defineProps<{
    invalid: boolean
    searching: boolean
}>()

const query = defineModel<SearchQuery>({required: true})

const {t} = useI18n()
</script>

<template>
  <div class="flex flex-wrap items-center gap-4">
    <div class="min-w-64 flex-1">
      <SearchInput v-model="query.text" :placeholder="t('ui.debugSearchBar.placeholder')"/>
    </div>
    <label class="flex items-center gap-2 text-xs"><CompactToggle v-model="query.regex"/> {{ t('ui.debugSearchBar.regex') }}</label>
    <label class="flex items-center gap-2 text-xs"><CompactToggle v-model="query.caseSensitive"/> {{ t('ui.debugSearchBar.caseSensitive') }}</label>
    <MutedText v-if="invalid" class="text-error" size="xs">{{ t('ui.debugSearchBar.invalidRegex') }}</MutedText>
    <Spinner v-else-if="searching" size="sm"/>
  </div>
</template>
