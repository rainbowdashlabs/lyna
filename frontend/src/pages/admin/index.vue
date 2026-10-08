/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {useRouter} from 'vue-router'
import {useAdminGuilds} from '~/composables/useAdminGuilds'
import {useSession} from '~/composables/useSession'

/**
 * Where the admin area starts: straight on to the one guild somebody administers, a choice among
 * several, or - for somebody who administers nothing - why not and what to do about it, rather than a
 * silent return to the account area.
 */
const {t} = useI18n()

const router = useRouter()
const {guilds, load} = useAdminGuilds()
const {account} = useSession()
const refused = ref(false)

onMounted(async () => {
  await load(true)
  if (guilds.value.length === 0) {
    refused.value = true
    return
  }
  if (guilds.value.length === 1) {
    await router.replace(`/admin/g/${guilds.value[0]!.id}/products`)
    return
  }
  await router.replace('/admin/select')
})
</script>

<template>
  <div class="flex min-h-screen items-center justify-center px-4">
    <div v-if="refused" class="max-w-md space-y-4">
      <Heading :level="1">{{ t('page.admin.noAccess.title') }}</Heading>
      <p>{{ account?.discordId ? t('page.admin.noAccess.noGuild') : t('page.admin.noAccess.linkDiscord') }}</p>
      <NuxtLink class="font-data text-sm text-primary underline" to="/account">{{ t('page.admin.noAccess.toAccount') }}</NuxtLink>
    </div>
    <p v-else class="text-sm opacity-70">
      {{ t('page.admin.resolvingAdminContext') }}
    </p>
  </div>
</template>
