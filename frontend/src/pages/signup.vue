<script lang="ts" setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {signup} from '~/api/account'
import {useSession} from '~/composables/useSession'

const router = useRouter()
const {setToken, hydrate} = useSession()

const email = ref('')
const password = ref('')
const passwordConfirm = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)

async function submit() {
  errorMessage.value = null
  if (password.value.length < 8) {
    errorMessage.value = 'Password must be at least 8 characters.'
    return
  }
  if (password.value !== passwordConfirm.value) {
    errorMessage.value = 'Passwords do not match.'
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
      errorMessage.value = 'An account with this email already exists.'
    } else if (typeof error.response?.data === 'string') {
      errorMessage.value = error.response.data
    } else {
      errorMessage.value = 'Signup failed. Please try again.'
    }
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      Sign up
    </PageHeader>
    <form class="space-y-4" @submit.prevent="submit">
      <LabelledField label="Email">
        <EmailInput v-model="email" autocomplete="email" required/>
      </LabelledField>
      <LabelledField label="Password">
        <PasswordInput v-model="password" autocomplete="new-password" minlength="8" required/>
      </LabelledField>
      <LabelledField label="Confirm password">
        <PasswordInput v-model="passwordConfirm" autocomplete="new-password" minlength="8" required/>
      </LabelledField>
      <Alert v-if="errorMessage" variant="error">{{ errorMessage }}</Alert>
      <PrimaryButton :disabled="submitting" full-width @click="submit">
        {{ submitting ? 'Creating account…' : 'Sign up' }}
      </PrimaryButton>
      <DiscordLoginLink/>
      <MutedText class="block text-center" size="sm" tag="p">
        Already have an account?
        <NuxtLink class="text-primary hover:underline" to="/login">Log in</NuxtLink>
      </MutedText>
    </form>
  </main>
</template>
