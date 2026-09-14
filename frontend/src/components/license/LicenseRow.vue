/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {LicenseView} from '~/api/account'

defineProps<{
    license: LicenseView
}>()
</script>

<template>
  <NuxtLink
      :to="`/account/licenses/${license.id}`"
      class="flex items-center gap-3 rounded-theme border border-border-light p-3 transition-colors hover:border-primary dark:border-border-dark"
  >
    <ProductMonogram :name="license.productName" size="sm"/>
    <div class="min-w-0 flex-1">
      <div class="truncate font-medium">{{ license.productName }}</div>
      <div class="flex flex-wrap items-center gap-1.5">
        <PrimaryBadge v-for="type in license.releaseTypes" :key="type">{{ type }}</PrimaryBadge>
        <MutedText v-if="license.role === 'owner'">
          {{ license.shareesUsed }} / {{ license.shareesCap }} sharees
        </MutedText>
        <MutedText v-else>Shared with you</MutedText>
      </div>
    </div>
    <MutedText size="sm">{{ license.role === 'owner' ? 'Manage →' : 'View →' }}</MutedText>
  </NuxtLink>
</template>
