/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import type {AccountInfo} from '~/api/account'

const {t} = useI18n()

defineProps<{
    account: AccountInfo
}>()
</script>

<template>
  <OverviewCard :title="t('ui.linkedAccountsCard.linkedAccounts')">
    <div class="space-y-2 text-sm">
      <div>
        <DetailLabel>{{ t('auth.email') }}</DetailLabel>
        <div>{{ account.email ?? 'Not set' }}</div>
      </div>
      <div>
        <DetailLabel>{{ t('ui.linkedAccountsCard.discord') }}</DetailLabel>
        <div>
          <template v-if="account.discordId">
            Linked as {{ account.username ?? account.discordId }}
          </template>
          <template v-else>
            {{ t('ui.linkedAccountsCard.notLinked') }} <AppLink href="/api/auth/discord/start">{{ t('ui.linkedAccountsCard.linkNow') }}</AppLink>
          </template>
        </div>
      </div>
    </div>
  </OverviewCard>
</template>
