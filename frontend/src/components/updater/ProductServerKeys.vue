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
  type ProductAllowance,
  reissueUpdateToken,
  renameUpdateToken,
  requestMoreUpdateTokens,
  revokeUpdateToken,
  type UpdateToken,
} from '~/api/updateTokens'
import {useKeyOverview} from '~/composables/useKeyOverview'

/**
 * The server keys a signed-in account holds of one product's key types, managed where the product is.
 * Shown only when the product hands out keys this account may hold or still holds.
 */
const props = defineProps<{ productId: number }>()

const {t} = useI18n()
const {data, busy, errorMessage, load, act, keysOf, lastRequestOf} = useKeyOverview()
/** A key just made, shown once with what to paste on the server. */
const fresh = ref<UpdateToken | null>(null)
const types = computed(() => data.value?.products.filter(allowance =>
    allowance.kind === 'SERVER' && allowance.productId === props.productId) ?? [])

onMounted(load)

async function create(type: ProductAllowance, name: string) {
  fresh.value = (await act(() => createUpdateToken(type.productId, name, type.keyTypeId))) ?? null
}

function freshFor(type: ProductAllowance): UpdateToken | null {
  return fresh.value?.keyTypeId === type.keyTypeId ? fresh.value : null
}
</script>

<template>
  <section v-if="types.length" :aria-label="t('ui.productServerKeys.title')" class="space-y-4" data-testid="product-server-keys">
    <SectionHeader>{{ t('ui.productServerKeys.title') }}</SectionHeader>
    <MutedText class="block" size="sm">{{ t('ui.productServerKeys.explain') }}</MutedText>
    <MutedText v-if="errorMessage" class="block text-error" role="alert" size="sm">{{ errorMessage }}</MutedText>
    <ServerKeyTypeSection
        v-for="type in types"
        :key="type.keyTypeId ?? 0"
        :busy="busy"
        :fresh="freshFor(type)"
        :keys="keysOf(type)"
        :last-request="lastRequestOf(type)"
        :product="type"
        @create="name => create(type, name)"
        @dismiss="fresh = null"
        @reissue="key => act(() => reissueUpdateToken(key.id))"
        @rename="(key, name) => act(() => renameUpdateToken(key.id, name))"
        @request="(amount, reason) => act(() => requestMoreUpdateTokens(type.productId, amount, reason, type.keyTypeId))"
        @revoke="key => act(() => revokeUpdateToken(key.id))"
    />
  </section>
</template>
