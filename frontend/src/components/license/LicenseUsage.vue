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
  <section>
    <CardHeader>{{ t('ui.licenseUsage.usage') }}</CardHeader>
    <ul v-if="rows.length" class="space-y-2 text-sm">
      <li v-for="row in rows" :key="row.id">
        <div class="font-medium">{{ row.productName }} {{ row.version }}</div>
        <MutedText tag="div">{{ formatRelative(row.downloadedAt) }} &middot; {{ row.source }}</MutedText>
      </li>
    </ul>
    <EmptyHint v-else>{{ t('ui.licenseUsage.nothingHasBeenDownloadedOnThis') }}</EmptyHint>
  </section>
</template>
