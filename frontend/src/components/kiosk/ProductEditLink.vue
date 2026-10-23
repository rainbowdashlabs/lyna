/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, watch} from 'vue'
import type {KioskProduct} from '~/api/kiosk'
import {useAdminGuilds} from '~/composables/useAdminGuilds'
import {useSession} from '~/composables/useSession'

/**
 * The way from a product's page to where it is edited, shown only to an account that administers
 * the guild the product belongs to. The admin area checks again on its own; this only decides
 * whether the link is worth showing.
 */
const props = defineProps<{ product: KioskProduct }>()

const {t} = useI18n()
const {account} = useSession()
const {guilds, load} = useAdminGuilds()

const shown = computed(() => account.value !== null && guilds.value.some(guild => guild.id === props.product.guildId))

watch(account, current => {
  if (current) load().catch(() => undefined)
}, {immediate: true})
</script>

<template>
  <NuxtLink
      v-if="shown"
      :to="`/admin/g/${product.guildId}/products/${product.id}`"
      class="rounded-theme inline-flex shrink-0 items-center gap-1.5 border border-(--border) px-3 py-1.5 text-sm font-medium whitespace-nowrap transition-colors hover:border-secondary hover:bg-secondary/12"
  >
    <font-awesome-icon :icon="['fas', 'pen']" class="h-3 w-3"/>
    {{ t('common.edit') }}
  </NuxtLink>
</template>
