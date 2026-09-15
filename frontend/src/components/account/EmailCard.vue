/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {ref} from 'vue'
import {changeEmail, resendVerification} from '~/api/account'

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
    message.value = 'Sent. Check the inbox, and the spam folder.'
  } catch (e) {
    report(e, 'Could not send it again.')
  } finally {
    busy.value = false
  }
}
</script>

<template>
  <section>
    <CardHeader>Email</CardHeader>
    <div class="space-y-2 text-sm">
      <div class="flex flex-wrap items-center gap-2">
        <span class="font-medium">{{ email ?? 'No address' }}</span>
        <SuccessBadge v-if="verified">Confirmed</SuccessBadge>
        <SecondaryBadge v-else-if="email">Not confirmed</SecondaryBadge>
      </div>
      <MutedText v-if="pendingEmail" tag="div">
        Waiting on {{ pendingEmail }} to be confirmed. This account keeps {{ email ?? 'no address' }} until it is.
      </MutedText>

      <div class="flex flex-wrap gap-2 pt-1">
        <SecondaryButton compact @click="changing = !changing">
          {{ changing ? 'Cancel' : 'Change email' }}
        </SecondaryButton>
        <SecondaryButton v-if="email && (!verified || pendingEmail)" :disabled="busy" compact @click="doResend">
          Send the link again
        </SecondaryButton>
      </div>

      <form v-if="changing" class="space-y-2 pt-2" @submit.prevent="submitChange">
        <LabelledField
            help="Nothing changes until the new address is confirmed from the mail sent to it."
            label="New email"
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
