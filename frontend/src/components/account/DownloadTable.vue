/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {DownloadRow} from '~/api/account'
import {formatDateTime} from '~/util/format'

const {t} = useI18n()

defineProps<{
    rows: DownloadRow[]
}>()
</script>

<template>
  <DataTable>
    <template #head>
      <Th>{{ t('ui.downloadTable.date') }}</Th>
      <Th>{{ t('common.product') }}</Th>
      <Th>{{ t('ui.downloadTable.version') }}</Th>
      <Th>{{ t('ui.downloadTable.source') }}</Th>
      <Th>{{ t('ui.downloadTable.license') }}</Th>
    </template>
    <TRow v-for="row in rows" :key="row.id">
      <Td>{{ formatDateTime(row.downloadedAt) }}</Td>
      <Td>
        <NuxtLink class="text-primary hover:underline" to="/">{{ row.productName }}</NuxtLink>
      </Td>
      <Td>{{ row.version }}</Td>
      <Td muted>{{ row.source }}</Td>
      <Td muted>{{ row.licenseId ?? '—' }}</Td>
    </TRow>
  </DataTable>
</template>
