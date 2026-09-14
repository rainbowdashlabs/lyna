<script lang="ts" setup>
import {ref} from 'vue'
import {requestPasswordReset} from '~/api/account'
import PrimaryButton from '~/components/button/PrimaryButton.vue'

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
      Reset password
    </PageHeader>
    <template v-if="!submitted">
      <p class="mb-4 text-sm opacity-70">
        Enter the email associated with your account. If we find a match we'll send a reset link valid for one hour.
      </p>
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
        <PrimaryButton :disabled="submitting" full-width @click="submit">
          {{ submitting ? 'Sending…' : 'Send reset link' }}
        </PrimaryButton>
      </form>
    </template>
    <p v-else class="rounded-theme border border-success/40 bg-success/10 p-3 text-sm">
      If the email matches an account, a reset link is on its way. Check your inbox (and spam) and follow the link within
      the next hour.
    </p>
    <p class="mt-6 text-center text-sm opacity-70">
      <NuxtLink to="/login" class="text-primary hover:underline">
        Back to login
      </NuxtLink>
    </p>
  </main>
</template>
