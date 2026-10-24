/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted, ref} from 'vue'
import {
  createUpdateToken,
  type KeyKind,
  type ProductAllowance,
  refusalOf,
  reissueUpdateToken,
  renameUpdateToken,
  requestMoreUpdateTokens,
  revokeUpdateToken,
  type UpdateToken,
  type UpdateTokenOverview,
  updateTokenOverview,
} from '~/api/updateTokens'
import {usePageTitle} from '~/composables/usePageTitle'

const {t} = useI18n()
usePageTitle(t('layout.account.updaters'))

definePageMeta({layout: 'account'})

const data = ref<UpdateTokenOverview | null>(null)
const loading = ref(true)
const busy = ref(false)
const errorMessage = ref<string | null>(null)
/** A dashboard key just made, shown once with what to paste on the server. */
const fresh = ref<UpdateToken | null>(null)

const upgradeProducts = computed(() => data.value?.products.filter(product => product.kind === 'UPDATE') ?? [])
const dashboardProducts = computed(() => data.value?.products.filter(product => product.kind === 'DASHBOARD') ?? [])

async function load() {
  try {
    data.value = await updateTokenOverview()
  } catch (e) {
    errorMessage.value = (e as Error).message
  } finally {
    loading.value = false
  }
}

onMounted(load)

/** Runs a change, says why if it was refused, and reloads either way so the counts stay true. */
async function act<T>(change: () => Promise<T>): Promise<T | undefined> {
  busy.value = true
  errorMessage.value = null
  try {
    return await change()
  } catch (e) {
    const refusal = refusalOf(e)
    errorMessage.value = refusal ? t(`page.account.updaters.refusal.${refusal}`) : (e as Error).message
    return undefined
  } finally {
    busy.value = false
    await load()
  }
}

async function createDashboardKey(product: ProductAllowance, name: string) {
  fresh.value = (await act(() => createUpdateToken(product.productId, name, 'DASHBOARD'))) ?? null
}

function keysOf(product: ProductAllowance): UpdateToken[] {
  return data.value?.tokens.filter(token => token.productId === product.productId && token.kind === product.kind) ?? []
}

function lastRequestOf(product: ProductAllowance) {
  return data.value?.requests.find(request => request.productId === product.productId && request.kind === product.kind) ?? null
}

function freshFor(product: ProductAllowance): UpdateToken | null {
  return fresh.value?.productId === product.productId ? fresh.value : null
}

function request(product: ProductAllowance, kind: KeyKind, amount: number, reason: string) {
  return act(() => requestMoreUpdateTokens(product.productId, amount, reason, kind))
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <PageHeader>{{ t('page.account.updaters.updaters') }}</PageHeader>
      <MutedText class="mt-1 block" size="sm">{{ t('page.account.updaters.serverKeysIntro') }}</MutedText>
    </div>
    <MutedText v-if="errorMessage" class="block text-error" role="alert" size="sm">{{ errorMessage }}</MutedText>
    <AsyncSection :loading="loading">
      <div v-if="data" class="space-y-10">
        <section :aria-label="t('page.account.updaters.upgradeTokens')" class="space-y-4">
          <SectionHeader>{{ t('page.account.updaters.upgradeTokens') }}</SectionHeader>
          <MutedText class="block" size="sm">{{ t('page.account.updaters.intro') }}</MutedText>
          <div v-if="upgradeProducts.length" class="space-y-8">
            <UpdaterProductSection
                v-for="product in upgradeProducts"
                :key="product.productId"
                :busy="busy"
                :last-request="lastRequestOf(product)"
                :product="product"
                :tokens="keysOf(product)"
                @create="name => act(() => createUpdateToken(product.productId, name))"
                @reissue="token => act(() => reissueUpdateToken(token.id))"
                @rename="(token, name) => act(() => renameUpdateToken(token.id, name))"
                @request="(amount, reason) => request(product, 'UPDATE', amount, reason)"
                @revoke="token => act(() => revokeUpdateToken(token.id))"
            />
          </div>
          <EmptyHint v-else>{{ t('page.account.updaters.nothingToUpdate') }}</EmptyHint>
          <UpdaterHowTo/>
        </section>
        <section :aria-label="t('page.account.updaters.dashboardKeys')" class="space-y-4">
          <SectionHeader>{{ t('page.account.updaters.dashboardKeys') }}</SectionHeader>
          <MutedText class="block" size="sm">{{ t('page.account.updaters.dashboardIntro') }}</MutedText>
          <div v-if="dashboardProducts.length" class="space-y-8">
            <DashboardProductSection
                v-for="product in dashboardProducts"
                :key="product.productId"
                :busy="busy"
                :fresh="freshFor(product)"
                :keys="keysOf(product)"
                :last-request="lastRequestOf(product)"
                :product="product"
                @create="name => createDashboardKey(product, name)"
                @dismiss="fresh = null"
                @reissue="key => act(() => reissueUpdateToken(key.id))"
                @rename="(key, name) => act(() => renameUpdateToken(key.id, name))"
                @request="(amount, reason) => request(product, 'DASHBOARD', amount, reason)"
                @revoke="key => act(() => revokeUpdateToken(key.id))"
            />
          </div>
          <EmptyHint v-else>{{ t('page.account.updaters.noDashboard') }}</EmptyHint>
        </section>
      </div>
    </AsyncSection>
  </div>
</template>
