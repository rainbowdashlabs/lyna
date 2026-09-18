/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {getTrialInfo, type TrialInfo} from '~/api/admin'
import Spinner from '~/components/feedback/Spinner.vue'

const {t} = useI18n()

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))

const info = ref<TrialInfo | null>(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    info.value = await getTrialInfo(guildId.value)
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  load()
})

onMounted(load)

function fmtMinutes(min: number): string {
  if (min < 60) return `${min} min`
  if (min < 60 * 24) return `${(min / 60).toFixed(1)} h`
  return `${(min / (60 * 24)).toFixed(1)} d`
}
</script>

<template>
  <div>
    <PageHeader class="mb-4">
      {{ t('page.admin.g.guildId.trial.trial') }}
    </PageHeader>
    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg" />
    </div>
    <template v-else-if="info">
      <section class="mb-4 rounded-theme border border-border-light dark:border-border-dark p-4 text-sm">
        <div class="text-xs uppercase tracking-wider opacity-60">
          {{ t('page.admin.g.guildId.trial.activeLimits') }}
        </div>
        <div class="mt-1">
          <span class="opacity-70">{{ t('page.admin.g.guildId.trial.perServerCooldown') }}</span> {{ fmtMinutes(info.serverMinutes) }}
        </div>
        <div>
          <span class="opacity-70">{{ t('page.admin.g.guildId.trial.perAccountCooldown') }}</span> {{ fmtMinutes(info.accountMinutes) }}
        </div>
        <p class="mt-2 opacity-60">
          {{ t('page.admin.g.guildId.trial.editTheseLimitsUnder') }}
          <NuxtLink :to="`/admin/g/${guildId}/settings`" class="text-primary hover:underline">
            {{ t('page.admin.g.guildId.trial.settings') }}
          </NuxtLink>.
        </p>
      </section>
      <section>
        <CardHeader>
          {{ t('page.admin.g.guildId.trial.products') }}
        </CardHeader>
        <ul v-if="info.products.length" class="divide-y divide-border-light dark:divide-border-dark rounded-theme border border-border-light dark:border-border-dark text-sm">
          <li v-for="p in info.products" :key="p.id" class="p-3">
            {{ p.name }}
            <span class="ml-2 opacity-60">id {{ p.id }}</span>
          </li>
        </ul>
        <div v-else class="rounded-theme border border-border-light dark:border-border-dark p-8 text-center opacity-70">
          {{ t('page.admin.g.guildId.trial.noProducts') }}
        </div>
      </section>
    </template>
  </div>
</template>
