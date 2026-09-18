/*
 *     SPDX-License-Identifier: AGPL-3.0-only
 *
 *     Copyright (C) RainbowDashLabs and Contributor
 */
<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {requestPasswordReset} from '~/api/account'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

const {t} = useI18n()

const email = ref('')
const submitting = ref(false)
const submitted = ref(false)

async function submit() {
  submitting.value = true
  try {
    await requestPasswordReset(email.value.trim())
  } finally {
    submitted.value = true
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      {{ t('page.forgot-password.resetPassword') }}
    </PageHeader>
    <template v-if="!submitted">
      <p class="mb-4 text-sm opacity-70">
        {{ t('page.forgot-password.enterTheEmailAssociatedWithYour') }}
      </p>
      <form class="space-y-4" @submit.prevent="submit">
        <LabelledField :label="t('auth.email')">
          <EmailInput v-model="email" autocomplete="email" required/>
        </LabelledField>
        <PrimaryButton :disabled="submitting" full-width @click="submit">
          {{ submitting ? 'Sending…' : 'Send reset link' }}
        </PrimaryButton>
      </form>
    </template>
    <p v-else class="rounded-theme border border-success/40 bg-success/10 p-3 text-sm">
      {{ t('page.forgot-password.ifTheEmailMatchesAnAccount') }}
    </p>
    <p class="mt-6 text-center text-sm opacity-70">
      <NuxtLink to="/login" class="text-primary hover:underline">
        {{ t('page.forgot-password.backToLogin') }}
      </NuxtLink>
    </p>
  </main>
</template>
