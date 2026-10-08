/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {redeemKey} from '~/api/account'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Entering a license key somebody was given, which makes the license this account's - what
 * {@code /register} does on Discord.
 */
const emit = defineEmits<{ redeemed: [] }>()

const {t} = useI18n()
const key = ref('')
const redeemedName = ref<string | null>(null)
const {busy, error, run} = useAdminAction(() => t('ui.redeemKey.couldNotRedeem'))

async function redeem() {
  const result = await run(() => redeemKey(key.value.trim()))
  if (!result) return
  redeemedName.value = result.productName
  key.value = ''
  emit('redeemed')
}
</script>

<template>
  <section class="space-y-2 border border-border-light p-4 dark:border-border-dark">
    <CardHeader>{{ t('ui.redeemKey.title') }}</CardHeader>
    <div class="flex flex-wrap items-end gap-3">
      <div class="min-w-64 flex-1">
        <LabelledField :label="t('ui.redeemKey.key')"><TextInput v-model="key" class="font-data"/></LabelledField>
      </div>
      <PrimaryButton :disabled="busy || !key.trim()" @click="redeem">{{ t('ui.redeemKey.redeem') }}</PrimaryButton>
    </div>
    <MutedText v-if="redeemedName" size="sm" tag="p">{{ t('ui.redeemKey.redeemed', {product: redeemedName}) }}</MutedText>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
