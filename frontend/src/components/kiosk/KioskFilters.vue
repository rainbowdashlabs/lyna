/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'
import type {StorefrontSort, StorefrontView} from '~/util/storefrontView'

const {t} = useI18n()

const filter = defineModel<string>('filter', {required: true})
const search = defineModel<string>('search', {required: true})
const view = defineModel<StorefrontView>('view', {required: true})
const sort = defineModel<StorefrontSort>('sort', {required: true})

const props = defineProps<{
    /** `Owned` is only meaningful once somebody is signed in, so it appears only then. */
    signedIn: boolean
    /**
     * The catalogue is fetched by the browser, so there is a moment where there is nothing to filter.
     * Typing into the box then loses what was typed - the first render after the list arrives writes
     * the model's empty string back over it - so the box is not offered until there is a list.
     */
    loading?: boolean
}>()

const chips = computed(() => props.signedIn
    ? [{key: 'all', label: 'All'}, {key: 'free', label: 'Free'}, {key: 'owned', label: 'Owned'}]
    : [{key: 'all', label: 'All'}, {key: 'free', label: 'Free'}])

const views = computed(() => [
    {key: 'tiles', label: t('ui.kioskFilters.tiles')},
    {key: 'list', label: t('ui.kioskFilters.list')},
])
</script>

<template>
  <div class="mb-4 space-y-3">
    <SearchInput v-model="search" :disabled="loading" :placeholder="t('ui.kioskFilters.searchPlugins')"/>
    <div class="flex flex-wrap items-center justify-between gap-2">
      <TabBar v-model="filter" :tabs="chips"/>
      <div class="flex items-center gap-2">
        <label class="flex items-center gap-2 text-sm">
          <MutedText size="sm">{{ t('ui.kioskFilters.sortBy') }}</MutedText>
          <SelectInput v-model="sort">
            <option value="name">{{ t('ui.kioskFilters.sortName') }}</option>
            <option value="updated">{{ t('ui.kioskFilters.sortUpdated') }}</option>
          </SelectInput>
        </label>
        <TabBar v-model="view" :tabs="views"/>
      </div>
    </div>
  </div>
</template>
