/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {computed, ref} from 'vue'
import type {ProductAllowance, UpdateToken, UpdateTokenRequest} from '~/api/updateTokens'

/**
 * The keys an account holds of one of a product's key types. An account that only took the trial holds
 * the type's trial allowance and cannot ask for more, so it is shown no request form.
 *
 * <p>A key just made is shown once, in the product's own command when the key type has one.
 */
const {t} = useI18n()

const props = defineProps<{
    product: ProductAllowance
    keys: UpdateToken[]
    /** The newest request for more keys of this type, if there was one. */
    lastRequest: UpdateTokenRequest | null
    /** A key just made of this type, shown once with what to paste on the server. */
    fresh: UpdateToken | null
    busy: boolean
}>()

defineEmits<{
    create: [name: string]
    rename: [key: UpdateToken, name: string]
    reissue: [key: UpdateToken]
    revoke: [key: UpdateToken]
    request: [amount: number, reason: string]
    dismiss: []
}>()

const name = ref('')
const full = computed(() => props.product.used >= props.product.allowed)
const command = computed(() => {
  if (!props.fresh) return ''
  const template = props.product.command
  return template ? template.replaceAll('{key}', props.fresh.token) : props.fresh.token
})

async function copy() {
  await navigator.clipboard.writeText(command.value)
}
</script>

<template>
  <section :aria-label="product.keyTypeName ?? ''" class="space-y-3">
    <CardHeader class="flex items-center justify-between">
      <span class="flex items-center gap-2">
        {{ product.keyTypeName }}
        <NeutralBadge v-if="product.trial">{{ t('common.trial') }}</NeutralBadge>
      </span>
      <MutedText class="font-data">{{ t('ui.serverKeyTypeSection.count', {used: product.used, allowed: product.allowed}) }}</MutedText>
    </CardHeader>
    <SecondaryContainer v-if="fresh" class="space-y-2" role="status">
      <MutedText class="block" size="sm">{{ t('ui.serverKeyTypeSection.pasteOnce', {name: fresh.name}) }}</MutedText>
      <CodeSnippet block class="break-all">{{ command }}</CodeSnippet>
      <div class="flex gap-1">
        <SecondaryButton compact @click="copy">{{ t('ui.updaterTokenRow.copy') }}</SecondaryButton>
        <SecondaryButton compact @click="$emit('dismiss')">{{ t('ui.serverKeyTypeSection.done') }}</SecondaryButton>
      </div>
    </SecondaryContainer>
    <div v-if="keys.length" class="text-sm" role="list">
      <UpdaterTokenRow
          v-for="(key, index) in keys"
          :key="key.id"
          :marker="index + 1"
          :token="key"
          :trial="product.trial"
          @reissue="$emit('reissue', key)"
          @rename="$emit('rename', key, $event)"
          @revoke="$emit('revoke', key)"
      />
    </div>
    <EmptyHint v-else>{{ t('ui.serverKeyTypeSection.noKeys') }}</EmptyHint>

    <form v-if="!full" class="flex flex-wrap items-center gap-2" @submit.prevent="$emit('create', name.trim()); name = ''">
      <TextInput v-model="name" :placeholder="t('ui.updaterProductSection.namePlaceholder')" class="min-w-48 flex-1"/>
      <PrimaryButton :disabled="busy || !name.trim()" compact type="submit">
        {{ t('ui.serverKeyTypeSection.add') }}
      </PrimaryButton>
    </form>
    <MutedText v-else-if="product.trial" class="block" size="sm">{{ t('ui.serverKeyTypeSection.trialLimit', {allowed: product.allowed}) }}</MutedText>
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
