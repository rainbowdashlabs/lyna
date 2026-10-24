/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, onMounted} from 'vue'
import {
  createUpdateToken,
  type ProductAllowance,
  reissueUpdateToken,
  renameUpdateToken,
  requestMoreUpdateTokens,
  revokeUpdateToken,
} from '~/api/updateTokens'
import {useKeyOverview} from '~/composables/useKeyOverview'
import {usePageTitle} from '~/composables/usePageTitle'

/**
 * The account's upgrade tokens, and where the keys of products' own key types are: those are managed
 * on each product's page, since what they are and how a server takes them is the product's to say.
 */
const {t} = useI18n()
usePageTitle(t('layout.account.updaters'))

definePageMeta({layout: 'account'})

const {data, loading, busy, errorMessage, load, act, keysOf, lastRequestOf} = useKeyOverview()

const upgradeProducts = computed(() => data.value?.products.filter(product => product.kind === 'UPDATE') ?? [])
const keyTypes = computed(() => data.value?.products.filter(product => product.kind === 'SERVER') ?? [])

onMounted(load)

function request(product: ProductAllowance, amount: number, reason: string) {
  return act(() => requestMoreUpdateTokens(product.productId, amount, reason))
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
                @request="(amount, reason) => request(product, amount, reason)"
                @revoke="token => act(() => revokeUpdateToken(token.id))"
            />
          </div>
          <EmptyHint v-else>{{ t('page.account.updaters.nothingToUpdate') }}</EmptyHint>
          <UpdaterHowTo/>
        </section>
        <section :aria-label="t('page.account.updaters.productKeys')" class="space-y-4">
          <SectionHeader>{{ t('page.account.updaters.productKeys') }}</SectionHeader>
          <MutedText class="block" size="sm">{{ t('page.account.updaters.productKeysIntro') }}</MutedText>
          <ul v-if="keyTypes.length" class="divide-y divide-border-light border border-border-light text-sm dark:divide-border-dark dark:border-border-dark" data-testid="product-key-links">
            <li v-for="type in keyTypes" :key="type.keyTypeId ?? 0" class="flex items-center justify-between gap-3 p-3">
              <NuxtLink :to="`/products/${type.productId}`" class="text-primary hover:underline">
                {{ type.productName }} · {{ type.keyTypeName }}
              </NuxtLink>
              <MutedText class="font-data">{{ t('ui.serverKeyTypeSection.count', {used: type.used, allowed: type.allowed}) }}</MutedText>
            </li>
          </ul>
          <EmptyHint v-else>{{ t('page.account.updaters.noProductKeys') }}</EmptyHint>
        </section>
      </div>
    </AsyncSection>
  </div>
</template>
