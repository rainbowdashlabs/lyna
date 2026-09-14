/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {formatRelative} from '~/util/format'

defineProps<{
    activeSessions: number
    lastSignInAt: string | null
}>()
</script>

<template>
  <OverviewCard title="Sessions">
    <div class="text-sm">
      <StatValue>{{ activeSessions }}</StatValue>
      <MutedText tag="div">active &middot; last sign-in {{ formatRelative(lastSignInAt) }}</MutedText>
      <NuxtLink class="mt-2 inline-block text-sm text-primary hover:underline" to="/account/security">
        Manage sessions &rarr;
      </NuxtLink>
    </div>
  </OverviewCard>
</template>
