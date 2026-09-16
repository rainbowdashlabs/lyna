/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {DownloadRow} from '~/api/account'
import {formatRelative} from '~/util/format'

const {t} = useI18n()

defineProps<{
    rows: DownloadRow[]
}>()
</script>

<template>
  <OverviewCard class="md:col-span-2" :title="t('ui.recentDownloadsCard.recentDownloads')">
    <MutedText v-if="rows.length === 0" size="sm" tag="div">
      {{ t('ui.recentDownloadsCard.noDownloadsYetNdashBrowseThe') }}
      <NuxtLink class="text-primary hover:underline" to="/">{{ t('ui.recentDownloadsCard.storefront') }}</NuxtLink>.
    </MutedText>
    <ul v-else class="space-y-2 text-sm">
      <li v-for="row in rows" :key="row.id" class="flex items-center justify-between">
        <div>
          <div class="font-medium">{{ row.productName }} {{ row.version }}</div>
          <MutedText tag="div">{{ formatRelative(row.downloadedAt) }} &middot; {{ row.source }}</MutedText>
        </div>
      </li>
    </ul>
  </OverviewCard>
</template>
