<script lang="ts" setup>
import {useI18n} from 'vue-i18n'
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {signup} from '~/api/account'
import {useSession} from '~/composables/useSession'

const {t} = useI18n()

const route = useRoute()
const router = useRouter()
const {setToken, hydrate} = useSession()

/**
 * The address a licence mail points here with, so somebody who bought with one address does not
 * sign up with another and leave the purchase behind. It stays editable: it is a suggestion, and
 * whoever is reading knows better than the link does.
 */
const invited = Array.isArray(route.query.email) ? route.query.email[0] : route.query.email
const email = ref(typeof invited === 'string' ? invited : '')
const password = ref('')
const passwordConfirm = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

async function submit() {
  errorMessage.value = null
  if (password.value.length < 8) {
    errorMessage.value = t('auth.passwordTooShort')
    return
  }
  if (password.value !== passwordConfirm.value) {
    errorMessage.value = t('auth.passwordsDiffer')
    return
  }
  submitting.value = true
  try {
    const result = await signup({email: email.value, password: password.value})
    setToken(result.token)
    await hydrate()
    await router.replace('/account')
  } catch (e) {
    const error = e as {response?: {status?: number, data?: string}}
    if (error.response?.status === 409) {
      errorMessage.value = t('page.signup.anAccountWithThisEmailAlready')
    } else if (typeof error.response?.data === 'string') {
      errorMessage.value = error.response.data
    } else {
      errorMessage.value = t('page.signup.signupFailedPleaseTryAgain')
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      {{ t('auth.signup') }}
    </PageHeader>
    <form class="space-y-4" @submit.prevent="submit">
      <LabelledField :label="t('auth.email')">
        <EmailInput v-model="email" autocomplete="email" required/>
      </LabelledField>
      <LabelledField :label="t('auth.password')">
        <PasswordInput v-model="password" autocomplete="new-password" minlength="8" required/>
      </LabelledField>
      <LabelledField :label="t('page.signup.confirmPassword')">
        <PasswordInput v-model="passwordConfirm" autocomplete="new-password" minlength="8" required/>
      </LabelledField>
      <Alert v-if="errorMessage" variant="error">{{ errorMessage }}</Alert>
      <PrimaryButton :disabled="submitting" full-width @click="submit">
        {{ submitting ? 'Creating account…' : 'Sign up' }}
      </PrimaryButton>
      <DiscordLoginLink/>
      <MutedText class="block text-center" size="sm" tag="p">
        {{ t('page.signup.alreadyHaveAnAccount') }}
        <NuxtLink class="text-primary hover:underline" to="/login">{{ t('auth.login') }}</NuxtLink>
      </MutedText>
    </form>
  </main>
</template>
