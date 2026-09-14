/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {computed} from 'vue'

const filter = defineModel<string>('filter', {required: true})
const search = defineModel<string>('search', {required: true})

const props = defineProps<{
    /** `Owned` is only meaningful once somebody is signed in, so it appears only then. */
    signedIn: boolean
}>()

const chips = computed(() => props.signedIn
    ? [{key: 'all', label: 'All'}, {key: 'free', label: 'Free'}, {key: 'owned', label: 'Owned'}]
    : [{key: 'all', label: 'All'}, {key: 'free', label: 'Free'}])
</script>

<template>
  <div class="mb-4 space-y-3">
    <SearchInput v-model="search" placeholder="Search plugins…"/>
    <TabBar v-model="filter" :tabs="chips"/>
  </div>
</template>
