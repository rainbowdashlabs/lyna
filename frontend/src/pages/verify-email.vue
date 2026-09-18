<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {onMounted, ref} from 'vue'
import {useRoute} from 'vue-router'
import {verifyEmail} from '~/api/account'

const {t} = useI18n()

const route = useRoute()

const state = ref<'working' | 'done' | 'failed'>('working')
const errorMessage = ref<string | null>(null)

onMounted(async () => {
  const raw = route.query.token
  const token = Array.isArray(raw) ? raw[0] : raw
  if (!token) {
    state.value = 'failed'
    errorMessage.value = t('page.verify-email.thatLinkCarriesNoToken')
    return
  }
  try {
    await verifyEmail(token)
    state.value = 'done'
  } catch (e) {
    const error = e as {response?: {data?: string}}
    state.value = 'failed'
    errorMessage.value = typeof error.response?.data === 'string'
        ? error.response.data
        : 'That link could not be used.'
  }
})
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      {{ t('page.verify-email.emailAddress') }}
    </PageHeader>
    <Spinner v-if="state === 'working'" size="lg"/>
    <Alert v-else-if="state === 'done'" variant="success">
      {{ t('page.verify-email.yourAddressIsConfirmed') }}
      <NuxtLink class="ml-1 text-primary hover:underline" to="/account/security">{{ t('page.verify-email.backToYourAccount') }}</NuxtLink>
    </Alert>
    <Alert v-else variant="error">
      {{ errorMessage }}
      <NuxtLink class="ml-1 text-primary hover:underline" to="/account/security">{{ t('page.verify-email.askForANewLink') }}</NuxtLink>
    </Alert>
  </main>
</template>
