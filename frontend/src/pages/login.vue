<script lang="ts" setup>
import {ref} from 'vue'
import {useI18n} from 'vue-i18n'
import {useRoute, useRouter} from 'vue-router'
import {login} from '~/api/account'
import {useSession} from '~/composables/useSession'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

const {t} = useI18n()
const route = useRoute()
const router = useRouter()
const {setToken, hydrate} = useSession()

const email = ref('')
const password = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

async function submit() {
  submitting.value = true
  errorMessage.value = null
  try {
    const result = await login({email: email.value, password: password.value})
    setToken(result.token)
    await hydrate()
    const next = typeof route.query.next === 'string' ? route.query.next : '/account'
    await router.replace(next)
  } catch (e) {
    const error = e as {response?: {status?: number}}
    errorMessage.value = error.response?.status === 401
        ? t('auth.invalidCredentials')
        : t('auth.loginFailed')
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      {{ t('auth.login') }}
    </PageHeader>
    <DemoAccountPicker class="mb-6"/>
    <form class="space-y-4" @submit.prevent="submit">
      <LabelledField :::label="t('page.login.tPageLoginTauthemail')">
        <EmailInput v-model="email" autocomplete="email" required/>
      </LabelledField>
      <LabelledField :::label="t('page.login.tPageLoginTauthpassword')">
        <PasswordInput v-model="password" autocomplete="current-password" required/>
      </LabelledField>
      <div v-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-2 text-sm text-error">
        {{ errorMessage }}
      </div>
      <PrimaryButton :disabled="submitting" full-width @click="submit">
        {{ submitting ? t('auth.signingIn') : t('auth.login') }}
      </PrimaryButton>
      <a
          href="/api/auth/discord/start"
          class="block w-full rounded-theme border border-border-light dark:border-border-dark py-2 text-center font-medium hover:bg-primary/10"
      >
        {{ t('auth.loginWithDiscord') }}
      </a>
      <p class="text-center text-sm opacity-70">
        {{ t('auth.noAccount') }} <NuxtLink to="/signup" class="text-primary hover:underline">
          {{ t('auth.signup') }}
        </NuxtLink>
        <span class="mx-2">·</span>
        <NuxtLink to="/forgot-password" class="text-primary hover:underline">
          {{ t('auth.forgotPassword') }}
        </NuxtLink>
      </p>
    </form>
  </main>
</template>
