<script lang="ts" setup>
import {ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {login} from '~/api/account'
import {useSession} from '~/composables/useSession'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

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
        ? 'Invalid email or password.'
        : 'Login failed. Please try again.'
  } finally {
    submitting.value = false
  }
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <h1 class="mb-6 text-2xl font-bold">
      Log in
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
            autocomplete="current-password"
            class="mt-1 w-full rounded-theme border border-border-light dark:border-border-dark bg-transparent px-3 py-2 focus:border-primary focus:outline-none"
        />
      </label>
      <div v-if="errorMessage" class="rounded-theme border border-error/40 bg-error/10 p-2 text-sm text-error">
        {{ errorMessage }}
      </div>
      <PrimaryButton :disabled="submitting" full-width @click="submit">
        {{ submitting ? 'Signing in…' : 'Log in' }}
      </PrimaryButton>
      <a
          href="/api/auth/discord/start"
          class="block w-full rounded-theme border border-border-light dark:border-border-dark py-2 text-center font-medium hover:bg-primary/10"
      >
        Continue with Discord
      </a>
      <p class="text-center text-sm opacity-70">
        No account? <NuxtLink to="/signup" class="text-primary hover:underline">
          Sign up
        </NuxtLink>
        <span class="mx-2">·</span>
        <NuxtLink to="/forgot-password" class="text-primary hover:underline">
          Forgot password?
        </NuxtLink>
      </p>
    </form>
  </main>
</template>
