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
    changed: []
    unlink: []
}>()
</script>

<template>
  <div class="space-y-4">
    <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
      <UsernameCard
          :name-is-theirs="account.nameIsTheirs"
          :username="account.username"
          @changed="$emit('changed')"
      />
    </section>

    <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
      <EmailList/>
    </section>

    <section class="rounded-theme border border-border-light dark:border-border-dark p-4">
      <DiscordLinkCard
          :account="account"
          :format="format"
          @unlink="$emit('unlink')"
      />
    </section>
  </div>
</template>
