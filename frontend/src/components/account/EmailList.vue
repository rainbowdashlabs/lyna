/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {onMounted, ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {type AccountEmail, addEmail, listEmails, makeEmailPrimary, removeEmail} from '~/api/account'

const {t} = useI18n()

const addresses = ref<AccountEmail[]>([])
const adding = ref(false)
const draft = ref('')
const busy = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)

async function refresh() {
  addresses.value = await listEmails()
}

onMounted(refresh)

async function submit() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await addEmail(draft.value)
    // Answered the same way whether or not somebody else holds it, so this says what was done rather
    // than what was found.
    message.value = t('ui.emailList.sent')
    draft.value = ''
    adding.value = false
    await refresh()
  } catch (e) {
    const error = e as {response?: {data?: string}}
    isError.value = true
    message.value = typeof error.response?.data === 'string'
        ? error.response.data
        : t('ui.emailList.couldNotAdd')
  } finally {
    busy.value = false
  }
}

async function promote(address: string) {
  await makeEmailPrimary(address)
  await refresh()
}

async function drop(address: string) {
  try {
    await removeEmail(address)
    await refresh()
  } catch {
    isError.value = true
    message.value = t('ui.emailList.cannotRemovePrimary')
  }
}
</script>

<template>
  <section>
    <CardHeader>{{ t('ui.emailList.title') }}</CardHeader>

    <div v-if="addresses.length" class="mb-3 text-sm">
      <GutterRow
          v-for="(address, index) in addresses"
          :key="address.address"
          :marker="index + 1"
          :pending="!address.verified"
      >
        <span class="flex min-w-0 flex-wrap items-center gap-2">
          <span class="font-data truncate">{{ address.address }}</span>
          <SecondaryBadge v-if="address.verified">{{ t('ui.emailList.confirmed') }}</SecondaryBadge>
          <PrimaryBadge v-else>{{ t('ui.emailList.unconfirmed') }}</PrimaryBadge>
          <NeutralBadge v-if="address.primary">{{ t('ui.emailList.writtenTo') }}</NeutralBadge>
        </span>
        <template #trailing>
          <span class="flex gap-2">
            <SecondaryButton
                v-if="address.verified && !address.primary"
                compact
                @click="promote(address.address)"
            >
              {{ t('ui.emailList.makePrimary') }}
            </SecondaryButton>
            <SecondaryButton v-if="!address.primary" compact @click="drop(address.address)">
              {{ t('common.remove') }}
            </SecondaryButton>
          </span>
        </template>
      </GutterRow>
    </div>

    <MutedText class="mb-3 block" size="sm">{{ t('ui.emailList.explain') }}</MutedText>

    <SecondaryButton compact @click="adding = !adding">
      {{ adding ? t('common.cancel') : t('ui.emailList.addAnother') }}
    </SecondaryButton>

    <form v-if="adding" class="space-y-2 pt-3" @submit.prevent="submit">
      <LabelledField :label="t('ui.emailList.newAddress')">
        <EmailInput v-model="draft" autocomplete="email" required/>
      </LabelledField>
      <PrimaryButton :disabled="busy || !draft.trim()" compact @click="submit">
        {{ busy ? t('ui.emailList.sending') : t('common.save') }}
      </PrimaryButton>
    </form>

    <MutedText v-if="message" :class="isError ? 'text-error' : 'text-success'" class="mt-2 block" size="sm">
      {{ message }}
    </MutedText>
  </section>
</template>
