/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DebugSection} from '~/api/debug'

/**
 * A block the plugin added about itself: a table when it sent pairs, its text otherwise.
 */
defineProps<{
    section: DebugSection
    content: string | undefined
}>()
</script>

<template>
  <section class="space-y-2">
    <SectionLabel>{{ section.name }}</SectionLabel>
    <dl v-if="section.entries?.length" class="grid grid-cols-[minmax(8rem,auto)_1fr] gap-x-6 gap-y-1 text-sm">
      <template v-for="entry in section.entries" :key="entry.key">
        <dt><DetailLabel>{{ entry.key }}</DetailLabel></dt>
        <dd class="font-data break-all">{{ entry.value }}</dd>
      </template>
    </dl>
    <pre v-if="content" class="font-data overflow-x-auto border border-border-light p-3 text-xs dark:border-border-dark">{{ content }}</pre>
  </section>
</template>
