/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AccountInfo} from '~/api/account'

defineProps<{
    account: AccountInfo
}>()
</script>

<template>
  <OverviewCard title="Linked accounts">
    <div class="space-y-2 text-sm">
      <div>
        <DetailLabel>Email</DetailLabel>
        <div>{{ account.email ?? 'Not set' }}</div>
      </div>
      <div>
        <DetailLabel>Discord</DetailLabel>
        <div>
          <template v-if="account.discordId">
            Linked as {{ account.username ?? account.discordId }}
          </template>
          <template v-else>
            Not linked &ndash; <AppLink href="/api/auth/discord/start">link now</AppLink>
          </template>
        </div>
      </div>
    </div>
  </OverviewCard>
</template>
