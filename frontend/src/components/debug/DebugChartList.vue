/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {DebugSection} from '~/api/debug'

/**
 * The charts the plugin sent, two side by side on wide screens.
 */
defineProps<{
    charts: DebugSection[]
}>()
</script>

<template>
  <div class="grid gap-6 lg:grid-cols-2">
    <DebugChart v-for="chart in charts" :key="chart.position" :section="chart"/>
  </div>
</template>
