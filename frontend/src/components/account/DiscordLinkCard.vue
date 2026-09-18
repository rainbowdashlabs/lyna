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
    /** Formats an instant for display, so this card does not carry a second date convention. */
    format: (value: string) => string
}>()

defineEmits<{
    unlink: []
}>()
</script>

<template>
  <section>
    <CardHeader>{{ t('ui.discordLinkCard.discord') }}</CardHeader>
    <div v-if="account.discordId" class="space-y-2 text-sm">
      <div>
        <span class="opacity-70">{{ t('ui.discordLinkCard.linkedAs') }}</span> {{ account.username ?? account.discordId }}
      </div>
      <MutedText v-if="account.discordLinkedAt" tag="div">
        Since {{ format(account.discordLinkedAt) }}
      </MutedText>
      <MutedText tag="div">
        {{ t('ui.discordLinkCard.whileThisIsLinkedDiscordSupplies') }}
      </MutedText>
      <div class="flex items-center gap-3 pt-2">
        <AppLink href="/api/auth/discord/start">{{ t('ui.discordLinkCard.reLink') }}</AppLink>
        <ErrorButton compact @click="$emit('unlink')">{{ t('ui.discordLinkCard.unlink') }}</ErrorButton>
      </div>
    </div>
    <div v-else class="text-sm">
      {{ t('ui.discordLinkCard.notLinked') }} <AppLink href="/api/auth/discord/start">{{ t('ui.discordLinkCard.linkYourDiscord') }}</AppLink> {{ t('ui.discordLinkCard.toClaimTheLicensesYourDiscord') }}
    </div>
  </section>
</template>
