/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DownloadRow} from '~/api/account'
import {formatRelative} from '~/util/format'

defineProps<{
    rows: DownloadRow[]
}>()
</script>

<template>
  <section>
    <CardHeader>Usage</CardHeader>
    <ul v-if="rows.length" class="space-y-2 text-sm">
      <li v-for="row in rows" :key="row.id">
        <div class="font-medium">{{ row.productName }} {{ row.version }}</div>
        <MutedText tag="div">{{ formatRelative(row.downloadedAt) }} &middot; {{ row.source }}</MutedText>
      </li>
    </ul>
    <EmptyHint v-else>Nothing has been downloaded on this license yet.</EmptyHint>
  </section>
</template>
