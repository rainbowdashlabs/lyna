/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed} from 'vue'

const {t} = useI18n()

const filter = defineModel<string>('filter', {required: true})
const search = defineModel<string>('search', {required: true})

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
</script>

<template>
  <div class="mb-4 space-y-3">
    <SearchInput v-model="search" :disabled="loading" :placeholder="t('ui.kioskFilters.searchPlugins')"/>
    <TabBar v-model="filter" :tabs="chips"/>
  </div>
</template>
