/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {type BuiltByBitView, getBuiltByBit} from '~/api/admin'
import Spinner from '~/components/feedback/Spinner.vue'
import {usePageTitle} from '~/composables/usePageTitle'

const {t} = useI18n()
usePageTitle(t('common.platform.BUILTBYBIT'))

definePageMeta({layout: 'admin'})

const route = useRoute()
const guildId = ref(String(route.params.guildId))
const view = ref<BuiltByBitView | null>(null)
const loading = ref(true)

async function load() {
  loading.value = true
  try {
    view.value = await getBuiltByBit(guildId.value)
  } finally {
    loading.value = false
  }
}

watch(() => route.params.guildId, (next) => {
  guildId.value = String(next)
  load()
})

onMounted(load)
</script>

<template>
  <div>
    <PageHeader class="mb-4">{{ t('common.platform.BUILTBYBIT') }}</PageHeader>
    <MutedText class="mb-4" size="sm" tag="p">{{ t('page.admin.g.guildId.builtbybit.intro') }}</MutedText>

    <div v-if="loading" class="flex justify-center py-12">
      <Spinner size="lg"/>
    </div>
    <template v-else-if="view">
      <section class="mb-4 space-y-2 rounded-theme border border-border-light p-4 dark:border-border-dark">
        <CardHeader>{{ t('page.admin.g.guildId.builtbybit.setup') }}</CardHeader>
        <LabelledField :label="t('page.admin.g.guildId.builtbybit.webhookUrl')">
          <CodeSnippet block class="break-all select-all" data-testid="builtbybit-webhook-url">{{ view.webhookUrl }}</CodeSnippet>
          <MutedText size="xs" tag="p">{{ t('page.admin.g.guildId.builtbybit.webhookSecretHint') }}</MutedText>
        </LabelledField>
        <LabelledField :label="t('page.admin.g.guildId.builtbybit.purchaseTemplate')">
          <CodeSnippet block class="break-all select-all">{{ view.purchaseTemplate }}</CodeSnippet>
        </LabelledField>
        <LabelledField :label="t('page.admin.g.guildId.builtbybit.keyUrl')">
          <CodeSnippet block class="break-all select-all" data-testid="builtbybit-key-url">{{ view.keyUrl }}</CodeSnippet>
          <MutedText size="xs" tag="p">{{ t('page.admin.g.guildId.builtbybit.keySecretHint') }}</MutedText>
        </LabelledField>
        <MutedText size="sm" tag="p">
          {{ view.apiEnabled ? t('page.admin.g.guildId.builtbybit.apiEnabled') : t('page.admin.g.guildId.builtbybit.apiDisabled') }}
        </MutedText>
      </section>

      <SectionHeader class="mb-2">{{ t('page.admin.g.guildId.builtbybit.calls') }}</SectionHeader>
      <ul v-if="view.calls.length" class="divide-y divide-border-light rounded-theme border border-border-light text-sm dark:divide-border-dark dark:border-border-dark" data-testid="builtbybit-calls">
        <li v-for="call in view.calls" :key="call.id" class="p-3">
          <details>
            <summary class="flex cursor-pointer flex-wrap items-center gap-x-4 gap-y-1">
              <span class="font-data opacity-70">{{ new Date(call.received).toLocaleString() }}</span>
              <span class="font-data">{{ t(`page.admin.g.guildId.builtbybit.call.${call.call}`) }}</span>
              <span class="font-data">{{ t(`page.admin.g.guildId.builtbybit.outcome.${call.outcome}`) }}</span>
              <span v-if="call.productName">{{ call.productName }}</span>
              <span v-if="call.memberId" class="font-data opacity-70">
                {{ t('page.admin.g.guildId.builtbybit.member', {id: call.memberId}) }}
              </span>
              <span v-if="call.resourceId" class="font-data opacity-70">
                {{ t('page.admin.g.guildId.builtbybit.resource', {id: call.resourceId}) }}<template v-if="call.addonId"> / {{ call.addonId }}</template>
              </span>
            </summary>
            <CodeSnippet block class="mt-2 break-all">{{ call.raw }}</CodeSnippet>
          </details>
        </li>
      </ul>
      <EmptyHint v-else>{{ t('page.admin.g.guildId.builtbybit.noCallsYet') }}</EmptyHint>
    </template>
  </div>
</template>
