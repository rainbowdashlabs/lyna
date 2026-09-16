/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import type {AccountInfo} from '~/api/account'

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
    <CardHeader>Discord</CardHeader>
    <div v-if="account.discordId" class="space-y-2 text-sm">
      <div>
        <span class="opacity-70">Linked as:</span> {{ account.username ?? account.discordId }}
      </div>
      <MutedText v-if="account.discordLinkedAt" tag="div">
        Since {{ format(account.discordLinkedAt) }}
      </MutedText>
      <MutedText tag="div">
        While this is linked, Discord supplies your username.
      </MutedText>
      <div class="flex items-center gap-3 pt-2">
        <AppLink href="/api/auth/discord/start">Re-link</AppLink>
        <ErrorButton compact @click="$emit('unlink')">Unlink</ErrorButton>
      </div>
    </div>
    <div v-else class="text-sm">
      Not linked. <AppLink href="/api/auth/discord/start">Link your Discord</AppLink> to claim the
      licenses your Discord id already holds, and to be granted the roles they carry.
    </div>
  </section>
</template>
