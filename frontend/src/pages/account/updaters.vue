/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {
  createUpdateToken,
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
async function act(change: () => Promise<unknown>) {
  busy.value = true
  errorMessage.value = null
  try {
    await change()
  } catch (e) {
    const refusal = refusalOf(e)
    errorMessage.value = refusal ? t(`page.account.updaters.refusal.${refusal}`) : (e as Error).message
  } finally {
    busy.value = false
    await load()
  }
}

function tokensOf(product: ProductAllowance): UpdateToken[] {
  return data.value?.tokens.filter(token => token.productId === product.productId) ?? []
}

function lastRequestOf(product: ProductAllowance) {
  return data.value?.requests.find(request => request.productId === product.productId) ?? null
}
</script>

<template>
  <div class="space-y-6">
    <div>
      <PageHeader>{{ t('page.account.updaters.updaters') }}</PageHeader>
      <MutedText class="mt-1 block" size="sm">{{ t('page.account.updaters.intro') }}</MutedText>
    </div>
    <MutedText v-if="errorMessage" class="block text-error" role="alert" size="sm">{{ errorMessage }}</MutedText>
    <AsyncSection
        :empty="!!data && data.products.length === 0"
        :empty-message="t('page.account.updaters.nothingToUpdate')"
        :loading="loading"
    >
      <div v-if="data" class="space-y-8">
        <UpdaterProductSection
            v-for="product in data.products"
            :key="product.productId"
            :busy="busy"
            :last-request="lastRequestOf(product)"
            :product="product"
            :tokens="tokensOf(product)"
            @create="name => act(() => createUpdateToken(product.productId, name))"
            @reissue="token => act(() => reissueUpdateToken(token.id))"
            @rename="(token, name) => act(() => renameUpdateToken(token.id, name))"
            @request="(amount, reason) => act(() => requestMoreUpdateTokens(product.productId, amount, reason))"
            @revoke="token => act(() => revokeUpdateToken(token.id))"
        />
      </div>
    </AsyncSection>
    <UpdaterHowTo/>
  </div>
</template>
