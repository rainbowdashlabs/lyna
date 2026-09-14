<script lang="ts" setup>
import {computed, ref} from 'vue'
import {useRoute, useRouter} from 'vue-router'
import {confirmPasswordReset} from '~/api/account'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

const route = useRoute()
const router = useRouter()
const token = computed(() => (typeof route.query.token === 'string' ? route.query.token : ''))

const password = ref('')
const confirm = ref('')
const submitting = ref(false)
const errorMessage = ref<string | null>(null)
const done = ref(false)

async function submit() {
  errorMessage.value = null
  if (!token.value) {
    errorMessage.value = 'Missing token. Use the link from your email.'
    return
  }
  if (password.value.length < 8) {
    errorMessage.value = 'Password must be at least 8 characters.'
    return
  }
  if (password.value !== confirm.value) {
    errorMessage.value = 'Passwords do not match.'
    return
  }
  submitting.value = true
  try {
    await confirmPasswordReset(token.value, password.value)
    done.value = true
  } catch (e) {
    const err = e as {response?: {status?: number, data?: string}}
    if (err.response?.status === 410) errorMessage.value = 'This reset link has expired. Request a new one.'
    else errorMessage.value = typeof err.response?.data === 'string' ? err.response.data : 'Reset failed.'
  } finally {
    submitting.value = false
  }
}

async function goToLogin() {
  await router.replace('/login')
}
</script>

<template>
  <main class="mx-auto flex min-h-screen max-w-md flex-col justify-center p-6">
    <PageHeader class="mb-6">
      Set a new password
    </PageHeader>
    <template v-if="!done">
      <form class="space-y-4" @submit.prevent="submit">
        <label class="block">
          <span class="text-sm font-medium">New password</span>
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
          <span class="text-sm font-medium">Confirm new password</span>
          <input
              v-model="confirm"
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
          {{ submitting ? 'Updating…' : 'Set password' }}
        </PrimaryButton>
      </form>
    </template>
    <div v-else class="space-y-4">
      <p class="rounded-theme border border-success/40 bg-success/10 p-3 text-sm">
        Password updated. You can log in now.
      </p>
      <PrimaryButton full-width @click="goToLogin">
        Go to login
      </PrimaryButton>
    </div>
  </main>
</template>
