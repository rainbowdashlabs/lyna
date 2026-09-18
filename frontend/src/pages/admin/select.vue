/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted} from 'vue'
import {useAdminGuilds} from '~/composables/useAdminGuilds'

const {t} = useI18n()

const {guilds, load} = useAdminGuilds()
onMounted(load)
</script>

<template>
  <main class="mx-auto max-w-2xl p-6">
    <PageHeader class="mb-4">
      {{ t('page.admin.select.selectAGuild') }}
    </PageHeader>
    <p class="mb-6 opacity-70">
      {{ t('page.admin.select.youAdministerMoreThanOneGuild') }}
    </p>
    <ul class="space-y-2">
      <li v-for="g in guilds" :key="g.id">
        <NuxtLink
            :to="`/admin/g/${g.id}/products`"
            class="flex items-center justify-between rounded-theme border border-border-light dark:border-border-dark p-3 hover:bg-primary/5"
        >
          <span>
            <span class="font-medium">{{ g.name }}</span>
            <span class="ml-2 text-xs opacity-60">{{ g.role === 'operator' ? 'operator' : 'guild admin' }}</span>
          </span>
          <span aria-hidden="true">→</span>
        </NuxtLink>
      </li>
      <li v-if="guilds.length === 0" class="text-sm opacity-70">
        {{ t('page.admin.select.noAdminAccess') }}
      </li>
    </ul>
  </main>
</template>
