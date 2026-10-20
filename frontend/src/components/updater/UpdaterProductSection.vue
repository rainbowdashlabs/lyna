/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import type {ProductAllowance, UpdateToken, UpdateTokenRequest} from '~/api/updateTokens'

const {t} = useI18n()

const props = defineProps<{
    product: ProductAllowance
    tokens: UpdateToken[]
    /** The newest request for this product, if there was one. */
    lastRequest: UpdateTokenRequest | null
    busy: boolean
}>()

defineEmits<{
    create: [name: string]
    rename: [token: UpdateToken, name: string]
    reissue: [token: UpdateToken]
    revoke: [token: UpdateToken]
    request: [amount: number, reason: string]
}>()

const name = ref('')
const full = computed(() => props.product.used >= props.product.allowed)
</script>

<template>
  <section :aria-label="product.productName" class="space-y-3">
    <CardHeader class="flex items-center justify-between">
      <span>{{ product.productName }}</span>
      <MutedText class="font-data">{{ product.used }} / {{ product.allowed }}</MutedText>
    </CardHeader>
    <div v-if="tokens.length" class="text-sm" role="list">
      <UpdaterTokenRow
          v-for="(token, index) in tokens"
          :key="token.id"
          :marker="index + 1"
          :token="token"
          @reissue="$emit('reissue', token)"
          @rename="$emit('rename', token, $event)"
          @revoke="$emit('revoke', token)"
      />
    </div>
    <EmptyHint v-else>{{ t('ui.updaterProductSection.noUpdaters') }}</EmptyHint>

    <form v-if="!full" class="flex flex-wrap items-center gap-2" @submit.prevent="$emit('create', name.trim()); name = ''">
      <TextInput v-model="name" :placeholder="t('ui.updaterProductSection.namePlaceholder')" class="min-w-48 flex-1"/>
      <PrimaryButton :disabled="busy || !name.trim()" compact type="submit">
        {{ t('ui.updaterProductSection.add') }}
      </PrimaryButton>
    </form>
    <div v-else-if="product.pending" class="flex items-center gap-2">
      <PrimaryBadge>{{ t('ui.updaterProductSection.requestPending') }}</PrimaryBadge>
      <MutedText size="sm">{{ t('ui.updaterProductSection.anAdminWillDecide') }}</MutedText>
    </div>
    <div v-else class="space-y-2">
      <MutedText v-if="lastRequest?.status === 'DECLINED'" class="block" size="sm">
        {{ t('ui.updaterProductSection.lastDeclined') }}
      </MutedText>
      <UpdaterRequestForm :busy="busy" @submit="(amount, reason) => $emit('request', amount, reason)"/>
    </div>
  </section>
</template>
