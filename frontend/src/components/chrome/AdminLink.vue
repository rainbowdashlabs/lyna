/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, watch} from 'vue'
import {useAdminGuilds} from '~/composables/useAdminGuilds'
import {useSession} from '~/composables/useSession'

/**
 * The way into the admin area from the storefront, shown only to an account that can administer
 * something. Asking costs one request, made once the page knows who is signed in.
 */
const {t} = useI18n()
const {account} = useSession()
const {guilds, load} = useAdminGuilds()

const shown = computed(() => account.value !== null && guilds.value.length > 0)

watch(account, current => {
  if (current) load().catch(() => undefined)
}, {immediate: true})
</script>

<template>
  <NuxtLink
      v-if="shown"
      class="font-data border-r border-border-light px-4 py-2.5 text-xs whitespace-nowrap text-(--text-muted) hover:text-(--text) dark:border-border-dark"
      to="/admin"
  >
    {{ t('common.admin') }}
  </NuxtLink>
</template>
