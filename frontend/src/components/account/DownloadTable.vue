/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DownloadRow} from '~/api/account'
import {formatDateTime} from '~/util/format'

defineProps<{
    rows: DownloadRow[]
}>()
</script>

<template>
  <DataTable>
    <template #head>
      <Th>Date</Th>
      <Th>Product</Th>
      <Th>Version</Th>
      <Th>Source</Th>
      <Th>License</Th>
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
