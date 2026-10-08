/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {transferLicense} from '~/api/account'
import {useAdminAction} from '~/composables/useAdminAction'

/**
 * Handing the license to another account. Every share of it ends, and it is gone from this account,
 * so it asks once more before it happens.
 */
const props = defineProps<{ licenseId: number, productName: string }>()
const emit = defineEmits<{ transferred: [] }>()

const {t} = useI18n()
const subject = ref('')
const confirming = ref(false)
const {busy, error, run} = useAdminAction(() => t('ui.licenseTransfer.couldNotTransfer'))

async function transfer() {
  await run(() => transferLicense(props.licenseId, subject.value.trim()))
  if (!error.value) emit('transferred')
  confirming.value = false
}
</script>

<template>
  <section class="space-y-2 border border-border-light p-4 dark:border-border-dark">
    <CardHeader>{{ t('ui.licenseTransfer.title') }}</CardHeader>
    <MutedText size="sm" tag="p">{{ t('ui.licenseTransfer.explain', {product: productName}) }}</MutedText>
    <div class="flex flex-wrap items-end gap-3">
      <div class="min-w-64 flex-1">
        <LabelledField :label="t('ui.licenseTransfer.to')"><TextInput v-model="subject"/></LabelledField>
      </div>
      <SecondaryButton v-if="!confirming" :disabled="busy || !subject.trim()" @click="confirming = true">{{ t('ui.licenseTransfer.transfer') }}</SecondaryButton>
      <ErrorButton v-else :disabled="busy" @click="transfer">{{ t('ui.licenseTransfer.confirm', {to: subject.trim()}) }}</ErrorButton>
    </div>
    <MutedText v-if="error" class="block text-error" size="sm">{{ error }}</MutedText>
  </section>
</template>
