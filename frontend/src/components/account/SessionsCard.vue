/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {formatRelative} from '~/util/format'

const {t} = useI18n()

defineProps<{
    activeSessions: number
    lastSignInAt: string | null
}>()
</script>

<template>
  <OverviewCard :title="t('ui.sessionsCard.sessions')">
    <div class="text-sm">
      <StatValue>{{ activeSessions }}</StatValue>
      <MutedText tag="div">active &middot; last sign-in {{ formatRelative(lastSignInAt) }}</MutedText>
      <NuxtLink class="mt-2 inline-block text-sm text-primary hover:underline" to="/account/security">
        {{ t('ui.sessionsCard.manageSessionsRarr') }}
      </NuxtLink>
    </div>
  </OverviewCard>
</template>
