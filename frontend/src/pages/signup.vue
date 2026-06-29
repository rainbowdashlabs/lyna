<script lang="ts" setup>
import {ref} from 'vue'
import {useRouter} from 'vue-router'
import {signup} from '~/api/account'
import {useSession} from '~/composables/useSession'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

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
    <h1 class="mb-6 text-2xl font-bold">
      Sign up
    </h1>
    <form class="space-y-4" @submit.prevent="submit">
      <label class="block">
        <span class="text-sm font-medium">Email</span>
        <input
            v-model="email"
            type="email"
            required
            autocomplete="email"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>
      <label class="block">
        <span class="text-sm font-medium">Password</span>
        <input
            v-model="password"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>
      <label class="block">
        <span class="text-sm font-medium">Confirm password</span>
        <input
            v-model="passwordConfirm"
            type="password"
            required
            minlength="8"
            autocomplete="new-password"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>
      <div v-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-2 text-sm text-error">
        {{ errorMessage }}
      </div>
      <PrimaryButton :disabled="submitting" full-width @click="submit">
        {{ submitting ? 'Creating account…' : 'Sign up' }}
      </PrimaryButton>
      <a
          href="/api/auth/discord/start"
          class="block w-full rounded-theme border border-border-light dark:border-border-dark py-2 text-center font-medium hover:bg-primary/10"
      >
        Continue with Discord
      </a>
      <p class="text-center text-sm opacity-70">
        Already have an account? <NuxtLink to="/login" class="text-primary hover:underline">
          Log in
        </NuxtLink>
      </p>
    </form>
  </main>
</template>
