/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref, watch} from 'vue'
import {useRoute} from 'vue-router'
import {
  decideUpdateTokenRequest,
  type HeldKey,
  heldKeys,
  pendingUpdateTokenRequests,
  type RaisedAllowance,
  raisedAllowances,
  revokeHeldKey,
  setAllowance,
} from '~/api/adminUpdateTokens'
import type {KeyKind, UpdateTokenRequest} from '~/api/updateTokens'
import {usePageTitle} from '~/composables/usePageTitle'

const {t} = useI18n()
usePageTitle(t('layout.admin.updaters'))

definePageMeta({layout: 'admin'})

const KINDS: KeyKind[] = ['UPDATE', 'DASHBOARD']

const route = useRoute()
const guildId = ref(String(route.params.guildId))
const kind = ref<KeyKind>('UPDATE')

const requests = ref<UpdateTokenRequest[]>([])
const raised = ref<RaisedAllowance[]>([])
const held = ref<HeldKey[]>([])
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref<string | null>(null)

async function load() {
  try {
    requests.value = await pendingUpdateTokenRequests(guildId.value, kind.value)
    raised.value = await raisedAllowances(guildId.value, kind.value)
    held.value = kind.value === 'DASHBOARD' ? await heldKeys(guildId.value, 'DASHBOARD') : []
  } catch (e) {
    errorMessage.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

async function act(change: () => Promise<unknown>) {
  busy.value = true
  errorMessage.value = null
  try {
    await change()
  } catch (e) {
    errorMessage.value = (e as Error).message
  } finally {
    busy.value = false
    await load()
  }
}

/** How many dashboard keys the account holding this one holds for the same product. */
function ofAccount(key: HeldKey): number {
  return held.value.filter(other => other.accountId === key.accountId && other.productId === key.productId).length
}

watch(() => route.params.guildId, next => {
  guildId.value = String(next)
  void load()
})

watch(kind, () => {
  void load()
})

onMounted(load)
</script>

<template>
  <div class="space-y-8">
    <div>
      <PageHeader>{{ t('page.admin.g.guildId.updaters.updaters') }}</PageHeader>
      <MutedText class="mt-1 block" size="sm">{{ t('page.admin.g.guildId.updaters.intro') }}</MutedText>
    </div>
    <div class="flex border-b border-border-light dark:border-border-dark" role="tablist">
      <TabButton v-for="option in KINDS" :key="option" :active="kind === option" @select="kind = option">
        {{ t(`page.admin.g.guildId.updaters.kind.${option}`) }}
      </TabButton>
    </div>
    <MutedText v-if="errorMessage" class="block text-error" role="alert" size="sm">{{ errorMessage }}</MutedText>
    <AsyncSection :loading="loading">
      <section :aria-label="t('page.admin.g.guildId.updaters.requests')" class="mb-8">
        <CardHeader>{{ t('page.admin.g.guildId.updaters.requests') }}</CardHeader>
        <div v-if="requests.length" class="text-sm" role="list">
          <UpdaterRequestRow
              v-for="(request, index) in requests"
              :key="request.id"
              :busy="busy"
              :marker="index + 1"
              :request="request"
              @decide="approve => act(() => decideUpdateTokenRequest(guildId, request.id, approve))"
          />
        </div>
        <EmptyHint v-else>{{ t('page.admin.g.guildId.updaters.noRequests') }}</EmptyHint>
      </section>
      <section :aria-label="t('page.admin.g.guildId.updaters.raised')" class="mb-8">
        <CardHeader>{{ t('page.admin.g.guildId.updaters.raised') }}</CardHeader>
        <div v-if="raised.length" class="text-sm" role="list">
          <RaisedAllowanceRow
              v-for="(allowance, index) in raised"
              :key="`${allowance.accountId}-${allowance.productId}`"
              :allowance="allowance"
              :busy="busy"
              :marker="index + 1"
              @save="granted => act(() => setAllowance(guildId, allowance.accountId, allowance.productId, granted, kind))"
          />
        </div>
        <EmptyHint v-else>{{ t('page.admin.g.guildId.updaters.noneRaised') }}</EmptyHint>
      </section>
      <section v-if="kind === 'DASHBOARD'" :aria-label="t('page.admin.g.guildId.updaters.held')">
        <CardHeader>{{ t('page.admin.g.guildId.updaters.held') }}</CardHeader>
        <div v-if="held.length" class="text-sm" role="list">
          <HeldKeyRow
              v-for="(key, index) in held"
              :key="key.id"
              :busy="busy"
              :held="key"
              :marker="index + 1"
              :of-account="ofAccount(key)"
              @revoke="act(() => revokeHeldKey(guildId, key.id))"
          />
        </div>
        <EmptyHint v-else>{{ t('page.admin.g.guildId.updaters.noneHeld') }}</EmptyHint>
      </section>
    </AsyncSection>
  </div>
</template>
