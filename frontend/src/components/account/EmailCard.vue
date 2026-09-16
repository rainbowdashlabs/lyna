/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {changeEmail, resendVerification} from '~/api/account'

const {t} = useI18n()

const props = defineProps<{
    email: string | null
    verified: boolean
    pendingEmail: string | null
}>()

const emit = defineEmits<{
    changed: []
}>()

const changing = ref(false)
const newEmail = ref('')
const busy = ref(false)
const message = ref<string | null>(null)
const isError = ref(false)

function report(e: unknown, fallback: string) {
  const error = e as {response?: {data?: string}}
  isError.value = true
  message.value = typeof error.response?.data === 'string' ? error.response.data : fallback
}

async function submitChange() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await changeEmail(newEmail.value)
    isError.value = false
    message.value = `Confirm the new address from the mail sent to ${newEmail.value}. Until then this account keeps the one it has.`
    newEmail.value = ''
    changing.value = false
    emit('changed')
  } catch (e) {
    report(e, 'Could not start the change.')
  } finally {
    busy.value = false
  }
}

async function doResend() {
  busy.value = true
  message.value = null
  isError.value = false
  try {
    await resendVerification()
    message.value = t('ui.emailCard.sentCheckTheInboxAndThe')
  } catch (e) {
    report(e, 'Could not send it again.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <CardHeader>{{ t('auth.email') }}</CardHeader>
    <div class="space-y-2 text-sm">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-medium">{{ email ?? 'No address' }}</span>
        <SecondaryBadge v-if="verified">{{ t('ui.emailCard.confirmed') }}</SecondaryBadge>
        <PrimaryBadge v-else-if="email">{{ t('ui.emailCard.notConfirmed') }}</PrimaryBadge>
      </div>
      <MutedText v-if="pendingEmail" tag="div">
        Waiting on {{ pendingEmail }} to be confirmed. This account keeps {{ email ?? 'no address' }} until it is.
      </MutedText>

      <div class="flex flex-wrap gap-2 pt-1">
        <SecondaryButton compact @click="changing = !changing">
          {{ changing ? 'Cancel' : 'Change email' }}
        </SecondaryButton>
        <SecondaryButton v-if="email && (!verified || pendingEmail)" :disabled="busy" compact @click="doResend">
          {{ t('ui.emailCard.sendTheLinkAgain') }}
        </SecondaryButton>
      </div>

      <form v-if="changing" class="space-y-2 pt-2" @submit.prevent="submitChange">
        <LabelledField
            :help="t('ui.emailCard.nothingChangesUntilTheNewAddress')"
            :label="t('ui.emailCard.newEmail')"
        >
          <EmailInput v-model="newEmail" autocomplete="email" required/>
        </LabelledField>
        <PrimaryButton :disabled="busy || !newEmail.trim()" compact @click="submitChange">
          {{ busy ? 'Sending…' : 'Send the confirmation' }}
        </PrimaryButton>
      </form>

      <MutedText v-if="message" :class="isError ? 'text-error' : 'text-success'" class="block" size="sm">
        {{ message }}
      </MutedText>
    </div>
  </section>
</template>
